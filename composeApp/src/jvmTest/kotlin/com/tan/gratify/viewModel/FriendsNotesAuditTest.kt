package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.model.social.DirectConversation
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.*
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.lang.reflect.Proxy
import kotlin.test.*

class FriendsNotesAuditTest {
    private inline fun <reified T> fake(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)) { _,method,_ -> answer(method.name) } as T
    @Test fun inboxLoadsEvenWhenRemovedStoryServiceWouldFailAndNeverRequestsThatFeed() = runBlocking(Dispatchers.Swing) {
        val client=createSupabaseClient("http://127.0.0.1:9","synthetic-key") { install(Auth) { autoLoadFromStorage=false;autoSaveToStorage=false;alwaysAutoRefresh=false } }
        client.auth.importSession(Json { ignoreUnknownKeys=true }.decodeFromString<UserSession>(
            """{"access_token":"synthetic-self","refresh_token":"synthetic-refresh","expires_in":3600,"token_type":"bearer","user":{"id":"self","aud":"authenticated","role":"authenticated","created_at":"2026-10-09T00:00:00Z","app_metadata":{},"user_metadata":{}}}"""),autoRefresh=false)
        var feeds=0;var inboxes=0
        val repository=fake<FriendsSocialRepository> { method -> when(method) {
            "stories" -> { feeds++;error("The removed story service is unavailable") }
            "inbox" -> { inboxes++; listOf(DirectConversation("chat","peer",accepted=true,requestFrom="self")) }
            else -> error("Unexpected social call: $method")
        } }
        val vm=FriendsSocialViewModel(repository,fake<UserRepository> { error(it) },fake<SearchRepository> { error(it) },
            fake<StreamRepository> { error(it) },fake<DataStoreManager> { error(it) },client,fake<LyricsCanvasRepository> { error(it) })
        try {
            vm.refresh(true)
            withTimeout(5000) { while(vm.state.value.loading) delay(10) }
            assertEquals(1,inboxes);assertEquals(0,feeds);assertNull(vm.state.value.error)
            assertEquals("chat",vm.state.value.conversations.single().id)
            assertFalse(vm.state.value.publicFeed);assertFalse(vm.state.value.moreStories);assertTrue(vm.state.value.stories.isEmpty())
            vm.moreStories();assertEquals(0,feeds)
        } finally { vm.stop();vm.viewModelScope.cancel();client.close() }
    }
}
