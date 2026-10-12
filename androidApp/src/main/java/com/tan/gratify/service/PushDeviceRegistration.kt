package com.tan.gratify.service

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.tan.data.sync.AccountAccess
import com.tan.data.sync.AccountSessionCoordinator
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PushDeviceRegistration(private val context: Context, private val client: SupabaseClient,
    private val sessions: AccountSessionCoordinator, scope: CoroutineScope) {
    init {
        scope.launch {
            combine(sessions.access, tokenChanges) { access, _ -> access }.collectLatest { access ->
                if (access !is AccountAccess.Ready || access.offline) return@collectLatest
                val preferences=context.getSharedPreferences("push_installation", Context.MODE_PRIVATE)
                val installation=preferences.getString("installation",null) ?: UUID.randomUUID().toString().also {
                    preferences.edit().putString("installation",it).apply()
                }
                var failureDelay=30_000L
                while (isActive) {
                    try {
                        val token=withTimeout(15_000) { suspendCancellableCoroutine<String> { continuation ->
                            FirebaseMessaging.getInstance().token.addOnSuccessListener { if(continuation.isActive)continuation.resume(it) }
                                .addOnFailureListener { if(continuation.isActive)continuation.resumeWithException(it) }
                        } }
                        if (client.auth.currentUserOrNull()?.id != access.owner) return@collectLatest
                        client.postgrest.rpc("gratify_register_push_device", buildJsonObject {
                            put("p_installation",installation);put("p_token",token);put("p_package",context.packageName)
                        })
                        failureDelay=30_000L
                        delay(6*60*60*1000L)
                    } catch (_: TimeoutCancellationException) {
                        delay(failureDelay);failureDelay=(failureDelay*2).coerceAtMost(15*60*1000L)
                    } catch (cancelled:CancellationException) { throw cancelled }
                    catch (_:Exception) { delay(failureDelay);failureDelay=(failureDelay*2).coerceAtMost(15*60*1000L) }
                }
            }
        }
    }
    companion object {
        private val tokenChanges=MutableStateFlow(0)
        fun tokenChanged() { tokenChanges.update { it+1 } }
    }
}
