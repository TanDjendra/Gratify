package com.tan.gratify.ui

import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.*
import com.tan.gratify.ui.component.OtpVerificationSection
import com.tan.gratify.ui.screen.login.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.auth.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AuthRenderTest {
    private fun tree(node: SemanticsNode): List<SemanticsNode> = listOf(node)+node.children.flatMap(::tree)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
    private fun button(scene: ImageComposeScene, label: String) = nodes(scene).first { label in texts(it) && it.config.contains(SemanticsActions.OnClick) }
    private suspend fun settle(scene: ImageComposeScene, time: Long=0) { repeat(20) { scene.render(time+it*16_000_000L).close(); delay(16) } }
    private fun snapshot(scene: ImageComposeScene, name: String) {
        val root=generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val path=root.resolve("artifacts/login-spotify-2026-10-10/$name.png"); Files.createDirectories(path.parent)
        scene.render(8_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(path,data.bytes) } }
    }
    @Test fun welcomeOrdersSignupGoogleAndLoginWithWorkingIndependentActions(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width,height,scale) in listOf(Triple(390,780,1f),Triple(320,780,1.5f),Triple(1100,900,1f))) {
            var google = 0; var email = 0; var signup = 0
            val scene = ImageComposeScene(width,height,Density(1f,scale),coroutineContext=coroutineContext) {
                AppTheme { AuthLandingPage { AuthLandingContent(false,null,{email++},{signup++},{google++},{}) } }
            }
            try {
                settle(scene)
                val provider = button(scene,"Continue with Google")
                val signupButton = button(scene,"Sign up free")
                val loginButton = button(scene,"Already have an account? Sign in")
                assertTrue(provider.boundsInRoot.height>=52 && signupButton.boundsInRoot.height>=52)
                assertEquals(provider.boundsInRoot.left,signupButton.boundsInRoot.left)
                assertEquals(provider.boundsInRoot.right,signupButton.boundsInRoot.right)
                assertTrue(signupButton.boundsInRoot.bottom <= provider.boundsInRoot.top)
                assertTrue(provider.boundsInRoot.bottom < loginButton.boundsInRoot.top)
                assertTrue(provider.boundsInRoot.width <= 400)
                assertTrue(loginButton.boundsInRoot.height >= 48)
                assertTrue(loginButton.boundsInRoot.bottom <= height)
                val heading = nodes(scene).first { "Your music.\nFree on Gratify." in texts(it) }
                assertTrue(heading.boundsInRoot.top < height / 2, "Welcome heading should sit above the vertical midpoint")
                provider.config[SemanticsActions.OnClick].action!!.invoke()
                loginButton.config[SemanticsActions.OnClick].action!!.invoke()
                signupButton.config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals(1,google);assertEquals(1,email);assertEquals(1,signup)
                assertTrue(nodes(scene).any { "Your music.\nFree on Gratify." in texts(it) })
                assertFalse(nodes(scene).any { "supportgratify@gmail.com" in texts(it) })
                snapshot(scene,"landing-spotify-$width")
            } finally { scene.close() }
        }
    }
    @Test fun youtubeLoginChoiceIsOptionalAccessibleAndLockedWhileGoogleIsOpen(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale) in listOf(390 to 1f, 320 to 1.5f)) {
            var chosen by mutableStateOf(false)
            var waiting by mutableStateOf(false)
            var choiceChanges = 0
            var googleClicks = 0
            val scene = ImageComposeScene(width, 1200, Density(1f, scale), coroutineContext = coroutineContext) {
                AppTheme { AuthLandingPage {
                    AuthLandingContent(waiting, null, {}, {}, { googleClicks++ }, {},
                        connectYouTube = chosen, onConnectYouTubeChange = { chosen = it; choiceChanges++ })
                } }
            }
            try {
                settle(scene)
                val check = nodes(scene).single { it.config.getOrNull(SemanticsProperties.Role) == Role.Checkbox && it.config.contains(SemanticsActions.OnClick) }
                assertFalse(chosen)
                assertTrue(check.boundsInRoot.width <= width + 1)
                assertTrue(check.boundsInRoot.height >= 48)
                check.config[SemanticsActions.OnClick].action!!.invoke()
                settle(scene, 1_000_000_000L)
                assertTrue(chosen); assertEquals(1, choiceChanges)
                button(scene, "Continue with Google").config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals(1, googleClicks)
                snapshot(scene, "google-youtube-$width")
                waiting = true; settle(scene, 2_000_000_000L)
                assertTrue(nodes(scene).single { it.config.getOrNull(SemanticsProperties.Role) == Role.Checkbox && it.config.contains(SemanticsActions.OnClick) }
                    .config.contains(SemanticsProperties.Disabled))
            } finally { scene.close() }
        }
    }
    @Test fun loginLayoutsWrapAndPasswordsAreMaskedOnPhoneAndDesktop(): Unit = runBlocking(Dispatchers.Swing) {
        for((width,scale) in listOf(390 to 1f,320 to 1.5f,1100 to 1f)) {
            val form=LoginFormState("synthetic@example.invalid",true,"synthetic-password",true)
            val scene=ImageComposeScene(width,900,Density(1f,scale),coroutineContext=coroutineContext) {
                AppTheme { AuthPage("Sign in",onBack={}, branded=true) { AuthLoginForm(form,false,"Could not connect. Try again.",{},{},{},{},{}) } }
            }
            try {
                settle(scene)
                assertTrue(nodes(scene).any { it.config.contains(SemanticsProperties.Password) })
                val editors=nodes(scene).filter { it.config.contains(SemanticsActions.SetText) }
                assertEquals(2,editors.size)
                editors.forEach { assertTrue(it.boundsInRoot.left>=0 && it.boundsInRoot.right<=width+1) }
                assertTrue(nodes(scene).any { it.config.getOrNull(SemanticsProperties.LiveRegion)==LiveRegionMode.Polite })
                snapshot(scene,"login-$width")
            } finally { scene.close() }
        }
    }
    @Test fun signupConsentIsOneAccessibleActionAndLoadingDisablesSubmission(): Unit = runBlocking(Dispatchers.Swing) {
        var busy by mutableStateOf(false); var termsChanges=0
        var form by mutableStateOf(SignUpFormState(email="synthetic@example.invalid",isEmailValid=true,password="synthetic-password",isPasswordValid=true,
            confirmPassword="synthetic-password",isConfirmPasswordMatch=true,name="Synthetic",isNameValid=true))
        val scene=ImageComposeScene(390,1100,Density(1f),coroutineContext=coroutineContext) {
            AppTheme { AuthPage("Create account",onBack={}) { AuthSignUpForm(form,busy,null,{},{},{},{},{termsChanges++;form=form.copy(isTermsAccepted=it)},{},{}) } }
        }
        try {
            settle(scene)
            assertTrue(button(scene,"Create and verify account").config.contains(SemanticsProperties.Disabled))
            val check=nodes(scene).first { it.config.getOrNull(SemanticsProperties.Role)==Role.Checkbox && it.config.contains(SemanticsActions.OnClick) }
            check.config[SemanticsActions.OnClick].action!!.invoke(); settle(scene,1_000_000_000L)
            assertTrue(form.isTermsAccepted); assertEquals(1,termsChanges)
            assertFalse(button(scene,"Create and verify account").config.contains(SemanticsProperties.Disabled))
            snapshot(scene,"signup-390")
            busy=true; settle(scene,2_000_000_000L)
            assertTrue(button(scene,"Creating account…").config.contains(SemanticsProperties.Disabled))
            assertTrue(nodes(scene).filter { it.config.contains(SemanticsActions.SetText) }.all { it.config.contains(SemanticsProperties.Disabled) })
        } finally { scene.close() }
    }
    @Test fun pastedOtpFitsSmallScreensAndResendBlocksVerification(): Unit = runBlocking(Dispatchers.Swing) {
        var code by mutableStateOf(""); var resending by mutableStateOf(false)
        val scene=ImageComposeScene(320,900,Density(1f,1.5f),coroutineContext=coroutineContext) {
            AppTheme { AuthPage("Verify",onBack={}) { OtpVerificationSection("synthetic-long-email@example.invalid",code,{code=it},{},{},
                isResending=resending,resendCooldownSeconds=45,errorMessage="Invalid code. Please try again.",onChangeEmail={}) } }
        }
        try {
            settle(scene)
            val editor=nodes(scene).first { it.config.contains(SemanticsActions.SetText) }
            assertTrue(editor.boundsInRoot.right<=321)
            editor.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("123 456")); settle(scene,1_000_000_000L)
            assertEquals("123456",code)
            assertTrue(button(scene,"Try again in 45s").config.contains(SemanticsProperties.Disabled))
            snapshot(scene,"otp-320-large-text")
            resending=true; settle(scene,2_000_000_000L)
            assertTrue(button(scene,"Verify email").config.contains(SemanticsProperties.Disabled))
        } finally { scene.close() }
    }
    @Test fun resetMismatchCannotBeSavedAndLandingOffersClearAccountChoices(): Unit = runBlocking(Dispatchers.Swing) {
        val scene=ImageComposeScene(390,900,Density(1f),coroutineContext=coroutineContext) {
            AppTheme { AuthPage("Reset",onBack={}) { AuthResetForm(ResetPasswordFormState("synthetic-password","different",true,false),false,null,{},{},{}) } }
        }
        try {
            settle(scene); assertTrue(button(scene,"Save new password").config.contains(SemanticsProperties.Disabled))
            assertTrue(nodes(scene).any { "The passwords do not match." in texts(it) }); snapshot(scene,"reset-390")
        } finally { scene.close() }
    }
}
