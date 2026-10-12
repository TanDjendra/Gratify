package com.tan.gratify.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.*
import com.tan.gratify.ui.component.SettingItem
import com.tan.gratify.ui.screen.home.*
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.SettingAlertState
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class SettingsRenderTest {
    private fun tree(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::tree)
    private fun nodes(s: ImageComposeScene) = s.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun texts(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
    private fun click(s: ImageComposeScene, label: String) = nodes(s).first { label in texts(it) && it.config.contains(SemanticsActions.OnClick) }
        .config[SemanticsActions.OnClick].action!!.invoke()
    private suspend fun settle(s: ImageComposeScene, time: Long = 0) { repeat(22) { s.render(time + it * 16_000_000L).close(); delay(16) } }
    private fun snapshot(s: ImageComposeScene, name: String) {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val path = root.resolve("artifacts/settings-audit-2026-10-08/$name.png")
        Files.createDirectories(path.parent)
        s.render(8_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(path, data.bytes) } }
    }
    @Test fun searchFindsTheActualSectionAndEmptyResultsOfferClearSearch(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale) in listOf(390 to 1f, 320 to 1.5f, 1100 to 1f)) {
            var query by mutableStateOf(""); var selected: Pair<SettingCategory, String?>? = null
            val scene = ImageComposeScene(width, 900, Density(1f, scale), coroutineContext = coroutineContext) {
                AppTheme { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    SettingsHomeContent(query, { query = it }, { category, section -> selected = category to section },
                        Modifier.widthIn(max=760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp))
                } }
            }
            try {
                settle(scene); snapshot(scene, "settings-home-$width")
                nodes(scene).first { it.config.contains(SemanticsActions.SetText) }.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("crossfade"))
                settle(scene,1_000_000_000L); click(scene, "Crossfade (BETA)")
                assertEquals(SettingCategory.PLAYBACK to "crossfade_settings", selected)
                query = "AI provider"; settle(scene,2_000_000_000L); click(scene, "AI provider")
                assertEquals(SettingCategory.LIRIK to "AI", selected); snapshot(scene, "settings-search-$width")
                query = "zzznomatch"; settle(scene,9_000_000_000L)
                assertTrue(nodes(scene).any { "No matching settings" in texts(it) })
                val clear = nodes(scene).first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Clear settings search") == true && it.config.contains(SemanticsActions.OnClick) }
                clear.config[SemanticsActions.OnClick].action!!.invoke(); assertEquals("",query)
            } finally { scene.close() }
        }
    }
    @Test fun wholeSettingRowTogglesOnceAndDisabledRowDoesNotWrite(): Unit = runBlocking(Dispatchers.Swing) {
        var checked by mutableStateOf(false); var enabled by mutableStateOf(true); var writes=0
        val scene=ImageComposeScene(320,600,Density(1f,1.5f),coroutineContext=coroutineContext) {
            AppTheme { SettingItem("History", "A detailed description that should remain readable with larger text.", isEnable=enabled,
                switch=checked to { checked=it; writes++ }) }
        }
        try {
            settle(scene); click(scene,"History"); assertTrue(checked); assertEquals(1,writes)
            enabled=false; settle(scene,1_000_000_000L)
            assertTrue(nodes(scene).first { "History" in texts(it) && it.config.contains(SemanticsActions.OnClick) }.config.contains(SemanticsProperties.Disabled))
            snapshot(scene,"settings-row-large-text")
        } finally { scene.close() }
    }
    @Test fun secretInputIsMaskedAndInvalidValuesCannotBeConfirmed(): Unit = runBlocking(Dispatchers.Swing) {
        var saves=0; var dismisses=0
        var state by mutableStateOf(SettingAlertState("Private value", textField=SettingAlertState.TextFieldData("Key", "synthetic-secret", sensitive=true,
            verifyCodeBlock={ (it.isNotBlank()) to "Required" }), confirm="Save" to { saves++ }, dismiss="Cancel"))
        val scene=ImageComposeScene(390,650,Density(1f,1.5f),coroutineContext=coroutineContext) {
            AppTheme { SettingsValueDialog(state, { state=it }, { dismisses++ }) }
        }
        try {
            settle(scene)
            val editor=nodes(scene).first { it.config.contains(SemanticsActions.SetText) }
            assertTrue(editor.config.contains(SemanticsProperties.Password))
            editor.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("")); settle(scene,1_000_000_000L)
            assertTrue(nodes(scene).first { "Save" in texts(it) && it.config.contains(SemanticsActions.OnClick) }.config.contains(SemanticsProperties.Disabled))
            assertEquals(0,saves); click(scene,"Cancel"); assertEquals(1,dismisses)
        } finally { scene.close() }
    }
    @Test fun selectingAndCancellingChoicesDoesNotApplyThem(): Unit = runBlocking(Dispatchers.Swing) {
        val applied=mutableListOf<String>(); var dismissed=0
        var state by mutableStateOf(SettingAlertState("Choices", selectOne=SettingAlertState.SelectData(listOf(true to "First",false to "Second")),
            confirm="Save" to { applied.add(it.selectOne!!.getSelected()) }, dismiss="Cancel"))
        val scene=ImageComposeScene(320,600,Density(1f,1.5f),coroutineContext=coroutineContext) {
            AppTheme { SettingsValueDialog(state,{state=it},{dismissed++}) }
        }
        try {
            settle(scene); click(scene,"Second"); settle(scene,1_000_000_000L)
            assertEquals("Second",state.selectOne!!.getSelected()); click(scene,"Cancel"); assertTrue(applied.isEmpty())
            click(scene,"Save"); assertEquals(listOf("Second"),applied); assertEquals(2,dismissed)
        } finally { scene.close() }
    }
}
