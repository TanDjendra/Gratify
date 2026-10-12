package com.tan.gratify

import androidx.lifecycle.viewModelScope
import com.sun.net.httpserver.HttpServer
import com.tan.data.sync.UserDataSyncManager
import com.tan.domain.data.model.sync.SyncReport
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.mediaservice.handler.*
import com.tan.domain.repository.*
import com.tan.gratify.viewModel.*
import com.tan.gratify.viewModel.auth.*
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import kotlinx.serialization.json.*
import org.koin.core.context.*
import org.koin.dsl.module
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

class AuthViewModelAuditTest {
    private inline fun <reified T> fake(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> answer(method.name, args.orEmpty()) } as T
    private class MemorySessions : SessionManager {
        var saved: UserSession? = null
        override suspend fun loadSession() = saved
        override suspend fun saveSession(session: UserSession) { saved = session }
        override suspend fun deleteSession() { saved = null }
    }
    private inner class Fixture {
        val calls = CopyOnWriteArrayList<Pair<String, JsonObject>>()
        val values = MutableStateFlow<Map<String,String>>(emptyMap())
        var activationFails = false
        var owner: String? = null
        val user = """{"id":"synthetic-user","aud":"authenticated","role":"authenticated","email":"synthetic@example.invalid","created_at":"2026-10-08T00:00:00Z","app_metadata":{},"user_metadata":{"display_name":"Synthetic"}}"""
        val session = """{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","expires_in":3600,"token_type":"bearer","user":$user}"""
        var response: (String) -> Pair<Int,String> = { path -> when(path) {
            "/auth/v1/signup" -> 200 to user
            "/auth/v1/resend", "/auth/v1/recover" -> 200 to "{}"
            else -> 400 to """{"msg":"Invalid login credentials","error_code":"invalid_credentials"}"""
        } }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0).apply {
            createContext("/auth/v1/") { exchange ->
                val path=exchange.requestURI.path
                val input=exchange.requestBody.readBytes().decodeToString()
                val body=runCatching { Json.parseToJsonElement(input).jsonObject }.getOrDefault(buildJsonObject {})
                calls += path to body
                val (status, payload) = response(path)
                val bytes=payload.toByteArray()
                exchange.responseHeaders.add("Content-Type","application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }; exchange.close()
            }; start()
        }
        val client=createSupabaseClient("http://127.0.0.1:${server.address.port}","synthetic-test-key") { install(Auth) { sessionManager=MemorySessions() } }
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Swing)
        val prefs=fake<DataStoreManager> { name,args -> when(name) {
            "getString" -> values.map { it[args[0] as String] }
            "putString" -> { values.value=values.value+(args[0] as String to args[1] as String); Unit }
            "setLoggedIn", "setCookie" -> Unit
            "getLoggedIn" -> flowOf(DataStoreManager.FALSE)
            "clearPerUserData" -> Unit
            "captureLegacyProfilePrivacy" -> emptyMap<String,Boolean>()
            else -> error("Unexpected preference $name")
        } }
        val handler=fake<MediaPlayerHandler> { name,_ -> when(name) {
            "getNowPlayingState" -> MutableStateFlow(NowPlayingTrackState.initial())
            "getControlState" -> MutableStateFlow(ControlState(false,false,RepeatState.None,false,false,false,false,1f))
            else -> error("Unexpected player $name")
        } }
        val repo=fake<UserDataSyncRepository> { name,args -> when(name) {
            "activateLocalAccount" -> { if(activationFails) error("Synthetic local storage unavailable"); owner=args[0] as String; Unit }
            "getLocalAccountOwner" -> owner
            "syncDown","syncUp" -> flowOf(Result.success(SyncReport()))
            else -> error("Unexpected sync $name")
        } }
        val social=fake<SocialRepository> { name,_ -> when(name) {
            "syncDownPlaylists" -> flowOf(Result.success(0))
            "flushPlaylistRemovals" -> Unit
            else -> error("Unexpected social $name")
        } }
        val users=fake<UserRepository> { name,_ -> if(name=="getUserProfile") flowOf(null) else error(name) }
        val youtube=fake<YouTubeLibraryRepository> { name,_ -> if(name=="isLoginConsentEnabled") false else error(name) }
        val sync=UserDataSyncManager(repo,social,client,prefs,scope,handler,users)
        lateinit var signup: SignUpViewModel
        lateinit var login: EmailLoginViewModel
        lateinit var recovery: ForgotPasswordViewModel
        suspend fun start() {
            client.auth.awaitInitialization()
            startKoin { modules(module { single<MediaPlayerHandler> { handler } }) }
            signup=SignUpViewModel(prefs,client,sync,youtube)
            login=EmailLoginViewModel(prefs,client,sync)
            recovery=ForgotPasswordViewModel(prefs,client)
        }
        suspend fun close() {
            signup.viewModelScope.cancel(); login.viewModelScope.cancel(); recovery.viewModelScope.cancel()
            scope.cancel(); client.close(); server.stop(0); stopKoin(); PasswordRecoveryCoordinator.clear()
        }
        fun completeForm() {
            signup.updateEmail("  synthetic@example.invalid  "); signup.updateName("Synthetic")
            signup.updatePassword("synthetic-password"); signup.updateConfirmPassword("synthetic-password"); signup.updateTermsAccepted(true)
        }
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f=Fixture(); f.start(); try { block(f) } finally { f.close() }
    }
    private suspend fun await(condition: () -> Boolean) = withTimeout(10_000) { while(!condition()) delay(10) }
    @Test fun signupCreatesCredentialsOnceAndEnforcesConsentAndResendCooldown(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f ->
            f.completeForm(); f.signup.updateTermsAccepted(false); f.signup.sendOtp(); delay(80); assertTrue(f.calls.isEmpty())
            f.signup.updateTermsAccepted(true); repeat(50) { f.signup.sendOtp() }
            await { f.signup.authState.value is AuthUiState.VerificationPending }
            assertEquals(1,f.calls.size)
            val (path,body)=f.calls.single()
            assertEquals("/auth/v1/signup",path); assertEquals("synthetic@example.invalid",body["email"]!!.jsonPrimitive.content)
            assertEquals("synthetic-password",body["password"]!!.jsonPrimitive.content)
            assertEquals("Synthetic",body["data"]!!.jsonObject["display_name"]!!.jsonPrimitive.content)
            assertTrue(f.signup.formState.value.password.isEmpty())
            repeat(30) { f.signup.resendOtp() }; delay(100); assertEquals(1,f.calls.size)
            f.signup.updateOtpCode("123 456\n"); f.signup.verifyOtp()
            await { f.signup.otpState.value.errorMessage != null }
            assertEquals("signup",f.calls.last().second["type"]!!.jsonPrimitive.content)
            assertEquals("synthetic@example.invalid",f.signup.otpState.value.email)
        }
    }
    @Test fun loginAcceptsOlderPasswordsNormalizesEmailAndRejectsDuplicateRequests(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f ->
            f.login.updateEmail(" synthetic@example.invalid "); f.login.updatePassword("short")
            assertTrue(f.login.formState.value.isValid())
            repeat(50) { f.login.signIn() }
            await { f.login.authState.value is AuthUiState.Error }
            assertEquals(1,f.calls.size)
            assertEquals("short",f.calls.single().second["password"]!!.jsonPrimitive.content)
            assertEquals("synthetic@example.invalid",f.calls.single().second["email"]!!.jsonPrimitive.content)
            f.login.updatePassword("changed"); assertIs<AuthUiState.Idle>(f.login.authState.value)
        }
    }
    @Test fun verifiedSignupRetriesLocalSetupWithoutConsumingCodeOrReplacingPassword(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f ->
            f.response={path -> when(path) { "/auth/v1/signup" -> 200 to f.user; "/auth/v1/verify" -> 200 to f.session; else -> 200 to "{}" } }
            f.completeForm(); f.signup.sendOtp(); await { f.signup.otpState.value.email != null }
            f.activationFails=true; f.signup.updateOtpCode("123456"); f.signup.verifyOtp()
            await { f.signup.otpState.value.verified && f.signup.otpState.value.errorMessage != null }
            f.activationFails=false; f.signup.verifyOtp(); await { f.signup.authState.value is AuthUiState.Authenticated }
            assertEquals(1,f.calls.count { it.first=="/auth/v1/verify" })
            assertFalse(f.calls.any { it.first=="/auth/v1/user" || it.first=="/auth/v1/otp" })
            assertEquals("synthetic-user",f.owner)
            assertTrue((f.signup.authState.value as AuthUiState.Authenticated).needsProfile)
        }
    }
    @Test fun resetLinkCannotBeRepeatedDuringCooldownAndUnverifiedSessionCannotChangePassword(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f ->
            f.recovery.updateEmail(" synthetic@example.invalid "); repeat(40) { f.recovery.sendResetLink() }
            await { f.recovery.authState.value is AuthUiState.PasswordResetSent }
            repeat(40) { f.recovery.sendResetLink() }; delay(100); assertEquals(1,f.calls.size)
            assertEquals("/auth/v1/recover",f.calls.single().first)
            PasswordRecoveryCoordinator.request()
            f.recovery.updateNewPassword("a long memorable phrase"); f.recovery.updateConfirmPassword("a long memorable phrase")
            assertTrue(f.recovery.resetFormState.value.isValid())
            f.recovery.setNewPassword(); assertIs<AuthUiState.Error>(f.recovery.authState.value)
            assertEquals(1,f.calls.size)
        }
    }
    @Test fun leavingVerifiedRecoveryClearsItsSessionAndSensitiveForm(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f ->
            f.client.auth.importSession(Json.decodeFromString<UserSession>(f.session))
            PasswordRecoveryCoordinator.request(); PasswordRecoveryCoordinator.verify("synthetic-user")
            f.recovery.markRecoveryReady(); assertTrue(f.recovery.recoveryReady)
            f.recovery.updateNewPassword("synthetic-password")
            var left=false; f.recovery.leaveRecovery { left=true }; await { left }
            assertNull(f.client.auth.currentUserOrNull()); assertFalse(PasswordRecoveryCoordinator.pending.value)
            assertTrue(f.recovery.resetFormState.value.newPassword.isEmpty())
        }
    }
    @Test fun signupConfirmationLinkCompletesOnlyThePendingEmail(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f ->
            f.completeForm(); f.signup.sendOtp(); await { f.signup.authState.value is AuthUiState.VerificationPending }
            f.client.auth.importSession(Json.decodeFromString<UserSession>(f.session.replace("synthetic@example.invalid", "other@example.invalid")))
            delay(120); assertIs<AuthUiState.VerificationPending>(f.signup.authState.value); assertNull(f.owner)
            f.client.auth.importSession(Json.decodeFromString<UserSession>(f.session))
            await { f.signup.authState.value is AuthUiState.Authenticated }
            assertEquals("synthetic-user",f.owner)
            assertFalse(f.calls.any { it.first=="/auth/v1/verify" || it.first=="/auth/v1/user" })
        }
    }
    @Test fun unconfirmedLoginSendsConfirmationAndKeepsTheOtpScreenAfterFailure(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f ->
            f.response={ path -> when(path) {
                "/auth/v1/token" -> 400 to """{"msg":"Email not confirmed","error_code":"email_not_confirmed"}"""
                "/auth/v1/resend" -> 200 to "{}"
                else -> 400 to """{"msg":"Token expired","error_code":"otp_expired"}"""
            } }
            f.login.updateEmail("synthetic@example.invalid"); f.login.updatePassword("synthetic-password"); f.login.signIn()
            await { f.login.otpState.value.resendCooldownSeconds > 0 && !f.login.otpState.value.isResending }
            assertIs<AuthUiState.VerificationPending>(f.login.authState.value)
            f.login.updateOtpCode("123456"); f.login.verifyOtp(); await { f.login.otpState.value.errorMessage != null }
            assertIs<AuthUiState.VerificationPending>(f.login.authState.value)
            assertEquals("synthetic@example.invalid",f.login.otpState.value.email)
            assertEquals("signup",f.calls.first { it.first=="/auth/v1/resend" }.second["type"]!!.jsonPrimitive.content)
        }
    }
}
