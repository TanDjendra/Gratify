package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.mediaservice.handler.*
import com.tan.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import org.koin.core.context.*
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.*

class SettingsViewModelAuditTest {
    private inline fun <reified T> fake(crossinline reply: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> reply(method.name, args.orEmpty()) } as T
    private class Preferences {
        val language = MutableStateFlow<String?>("en-US")
        val apiKey = MutableStateFlow("synthetic-key")
        val translation = MutableStateFlow(DataStoreManager.FALSE)
        val lyricTranslation = MutableStateFlow(DataStoreManager.FALSE)
        var failWrites = false
        val keepAlive = MutableStateFlow(DataStoreManager.FALSE)
        val killOnExit = MutableStateFlow(DataStoreManager.FALSE)
        val manager = Proxy.newProxyInstance(DataStoreManager::class.java.classLoader, arrayOf(DataStoreManager::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> language
                "getAiApiKey" -> apiKey
                "getUseAITranslation" -> translation
                "getEnableTranslateLyric" -> lyricTranslation
                "getKeepServiceAlive" -> keepAlive
                "getKillServiceOnExit" -> killOnExit
                "getYoutubeSubtitleLanguage" -> MutableStateFlow("en")
                "getHelpBuildLyricsDatabase" -> MutableStateFlow(DataStoreManager.FALSE)
                "putString" -> { language.value = args[1] as String; Unit }
                "setAIApiKey" -> { apiKey.value = args[0] as String; Unit }
                "setUseAITranslation" -> { translation.value = if(args[0] as Boolean) DataStoreManager.TRUE else DataStoreManager.FALSE; Unit }
                "setEnableTranslateLyric" -> { lyricTranslation.value = if(args[0] as Boolean) DataStoreManager.TRUE else DataStoreManager.FALSE; Unit }
                "setKeepServiceAlive" -> { if (failWrites) error("synthetic full disk"); keepAlive.value = if(args[0] as Boolean) DataStoreManager.TRUE else DataStoreManager.FALSE; Unit }
                "setKillServiceOnExit" -> { killOnExit.value = if(args[0] as Boolean) DataStoreManager.TRUE else DataStoreManager.FALSE; Unit }
                else -> error("Unexpected preference: ${method.name}")
            }
        } as DataStoreManager
    }
    private suspend fun fixture(block: suspend (SettingsViewModel, Preferences) -> Unit) {
        val preferences = Preferences()
        val player = fake<MediaPlayerHandler> { name, _ -> when(name) {
            "getNowPlayingState" -> MutableStateFlow(NowPlayingTrackState.initial())
            "getControlState" -> MutableStateFlow(ControlState(false,false,RepeatState.None,false,false,false,false,1f))
            else -> error(name)
        } }
        startKoin { modules(module { single<MediaPlayerHandler> { player } }) }
        val vm = SettingsViewModel(preferences.manager,
            fake<CommonRepository> { name, _ -> if(name=="getDatabasePath") null else error(name) },
            fake<SongRepository> { name, _ -> error(name) }, fake<AccountRepository> { name, _ -> error(name) },
            fake<CacheRepository> { name, _ -> error(name) })
        try { block(vm, preferences) } finally { vm.viewModelScope.cancel(); stopKoin() }
    }
    private suspend fun await(condition: () -> Boolean) = withTimeout(5000) { while(!condition()) delay(10) }
    @Test fun repeatedSettingsReadsDoNotAddPreferenceCollectors(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { vm, prefs ->
            repeat(100) { vm.getLanguage() }; await { vm.language.value=="en-US" }
            assertEquals(2,prefs.language.subscriptionCount.value) // language plus the existing lyrics-animation preference
            prefs.language.value="id-ID"; await { vm.language.value=="id-ID" }
            assertFalse(vm.settingsReadFailed.value)
        }
    }
    @Test fun removingApiKeyDisablesTranslationAndServiceModesStayExclusive(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { vm, prefs ->
            vm.getUseTranslation(); await { vm.useTranslation.value==DataStoreManager.FALSE }
            vm.setAIApiKey("synthetic-key"); await { vm.isHasApiKey.value }
            vm.setAITranslation(true); await { vm.useAITranslation.value || vm.settingsWriteFailed.value }
            assertFalse(vm.settingsWriteFailed.value, "Enabling AI translation must persist both translation preferences")
            assertTrue(vm.useAITranslation.value)
            assertEquals(DataStoreManager.TRUE,prefs.translation.value)
            await { vm.useTranslation.value==DataStoreManager.TRUE }
            assertEquals(DataStoreManager.TRUE,prefs.lyricTranslation.value)
            assertFalse(vm.settingsReadFailed.value)
            vm.setAIApiKey("  "); await { (!vm.useAITranslation.value && !vm.isHasApiKey.value && prefs.apiKey.value.isEmpty()) || vm.settingsWriteFailed.value }
            assertFalse(vm.settingsWriteFailed.value, "Removing the API key must disable AI translation")
            assertFalse(vm.useAITranslation.value)
            assertFalse(vm.isHasApiKey.value)
            assertEquals("",prefs.apiKey.value)
            assertEquals(DataStoreManager.FALSE,prefs.translation.value)
            assertEquals(DataStoreManager.TRUE,prefs.lyricTranslation.value) // Only AI translation requires an API key.
            vm.setKillServiceOnExit(true); await { vm.killServiceOnExit.value==DataStoreManager.TRUE }
            vm.getKillServiceOnExit(); vm.setKeepServiceAlive(true); await { vm.keepServiceAlive.value }
            await { vm.killServiceOnExit.value==DataStoreManager.FALSE }
            assertEquals(DataStoreManager.TRUE,prefs.keepAlive.value)
            assertEquals(DataStoreManager.FALSE,prefs.killOnExit.value)
            vm.setKillServiceOnExit(true); await { vm.killServiceOnExit.value==DataStoreManager.TRUE && !vm.keepServiceAlive.value }
            assertEquals(DataStoreManager.TRUE,prefs.killOnExit.value)
            assertEquals(DataStoreManager.FALSE,prefs.keepAlive.value)
            assertFalse(vm.settingsReadFailed.value)
            assertFalse(vm.settingsWriteFailed.value)
        }
    }
    @Test fun failedPreferenceWriteIsReportedAndCanBeRetried(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { vm, prefs ->
            prefs.failWrites=true; vm.setKeepServiceAlive(true); await { vm.settingsWriteFailed.value }
            assertFalse(vm.keepServiceAlive.value); assertEquals(DataStoreManager.FALSE,prefs.keepAlive.value)
            prefs.failWrites=false; vm.setKeepServiceAlive(true); await { vm.keepServiceAlive.value }
            assertFalse(vm.settingsWriteFailed.value)
        }
    }}
