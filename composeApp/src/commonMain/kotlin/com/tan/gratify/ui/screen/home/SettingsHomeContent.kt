package com.tan.gratify.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.theme.*
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal data class SettingsSearchEntry(val title: StringResource, val category: SettingCategory, val section: String, val keywords: String = "")
internal fun matchesSettingsSearch(query: String, title: String, category: String, keywords: String): Boolean =
    listOf(title, category, keywords).any { it.contains(query.trim(), ignoreCase = true) }
internal val settingsSearchEntries = listOf(
    SettingsSearchEntry(Res.string.youtube_account, SettingCategory.AKUN, "youtube_account_section"),
    SettingsSearchEntry(Res.string.log_out, SettingCategory.AKUN, "gratify_account"),
    SettingsSearchEntry(Res.string.settings_delete_account, SettingCategory.AKUN, "gratify_account"),
    SettingsSearchEntry(Res.string.language, SettingCategory.KONTEN_TAMPILAN, "content_display_section"),
    SettingsSearchEntry(Res.string.content_country, SettingCategory.KONTEN_TAMPILAN, "content_display_section"),
    SettingsSearchEntry(Res.string.play_explicit_content, SettingCategory.KONTEN_TAMPILAN, "content_display_section"),
    SettingsSearchEntry(Res.string.blur_player_background, SettingCategory.KONTEN_TAMPILAN, "user_interface"),
    SettingsSearchEntry(Res.string.blur_fullscreen_lyrics, SettingCategory.KONTEN_TAMPILAN, "user_interface"),
    SettingsSearchEntry(Res.string.local_tracking_title, SettingCategory.PRIVASI_SOSIAL, "privacy_social_section"),
    SettingsSearchEntry(Res.string.send_back_listening_data_to_google, SettingCategory.PRIVASI_SOSIAL, "privacy_social_section"),
    SettingsSearchEntry(Res.string.proxy, SettingCategory.KONEKSI, "network"),
    SettingsSearchEntry(Res.string.proxy_host, SettingCategory.KONEKSI, "network"),
    SettingsSearchEntry(Res.string.proxy_port, SettingCategory.KONEKSI, "network"),
    SettingsSearchEntry(Res.string.normalize_volume, SettingCategory.PLAYBACK, "audio"),
    SettingsSearchEntry(Res.string.skip_silent, SettingCategory.PLAYBACK, "audio"),
    SettingsSearchEntry(Res.string.save_playback_state, SettingCategory.PLAYBACK, "playback"),
    SettingsSearchEntry(Res.string.save_last_played, SettingCategory.PLAYBACK, "playback"),
    SettingsSearchEntry(Res.string.crossfade, SettingCategory.PLAYBACK, "crossfade_settings", "crossfade"),
    SettingsSearchEntry(Res.string.sponsorBlock, SettingCategory.PLAYBACK, "sponsor_block", "sponsor block sponsorblock"),
    SettingsSearchEntry(Res.string.quality, SettingCategory.KUALITAS_MEDIA, "media_quality_section"),
    SettingsSearchEntry(Res.string.video_quality, SettingCategory.KUALITAS_MEDIA, "media_quality_section"),
    SettingsSearchEntry(Res.string.download_quality, SettingCategory.MODE_HEMAT_DATA, "data_saver_section"),
    SettingsSearchEntry(Res.string.video_download_quality, SettingCategory.MODE_HEMAT_DATA, "data_saver_section"),
    SettingsSearchEntry(Res.string.keep_your_youtube_playlist_offline, SettingCategory.MODE_HEMAT_DATA, "data_saver_section"),
    SettingsSearchEntry(Res.string.lyrics_animation, SettingCategory.LIRIK, "lyrics"),
    SettingsSearchEntry(Res.string.main_lyrics_provider, SettingCategory.LIRIK, "lyrics"),
    SettingsSearchEntry(Res.string.translation_language, SettingCategory.LIRIK, "lyrics"),
    SettingsSearchEntry(Res.string.ai_provider, SettingCategory.LIRIK, "AI"),
    SettingsSearchEntry(Res.string.ai_api_key, SettingCategory.LIRIK, "AI", "API key kunci API"),
    SettingsSearchEntry(Res.string.use_ai_translation, SettingCategory.LIRIK, "AI"),
    SettingsSearchEntry(Res.string.player_cache, SettingCategory.PENYIMPANAN, "storage"),
    SettingsSearchEntry(Res.string.thumbnail_cache, SettingCategory.PENYIMPANAN, "storage"),
    SettingsSearchEntry(Res.string.cloud_backup_title, SettingCategory.PENYIMPANAN, "backup", "cloud backup cadangan awan"),
    SettingsSearchEntry(Res.string.cloud_restore_title, SettingCategory.PENYIMPANAN, "backup", "cloud restore pemulihan awan"),
    SettingsSearchEntry(Res.string.backup, SettingCategory.PENYIMPANAN, "backup", "backup cadangan"),
    SettingsSearchEntry(Res.string.restore_your_data, SettingCategory.PENYIMPANAN, "backup", "restore pemulihan"),
    SettingsSearchEntry(Res.string.version, SettingCategory.TENTANG_DUKUNGAN, "about_us"),
    SettingsSearchEntry(Res.string.check_for_update, SettingCategory.TENTANG_DUKUNGAN, "about_us"),
    SettingsSearchEntry(Res.string.about_developer_credits, SettingCategory.TENTANG_DUKUNGAN, "about_us", "developer pengembang Tan kredit credits"),
    SettingsSearchEntry(Res.string.about_contact_support, SettingCategory.TENTANG_DUKUNGAN, "about_us", "support bantuan email bug masalah"),
)

internal fun SettingCategory.sections(): List<String> = when(this) {
    SettingCategory.AKUN -> listOf("youtube_account_section", "google_link", "gratify_account")
    SettingCategory.KONTEN_TAMPILAN -> listOf("user_interface", "content_display_section")
    SettingCategory.PRIVASI_SOSIAL -> listOf("privacy_social_section")
    SettingCategory.KONEKSI -> listOf("network", "proxy")
    SettingCategory.PLAYBACK -> listOf("playback_video_track_section", "audio", "playback", "crossfade_settings", "sponsor_block")
    SettingCategory.LIRIK -> listOf("lyrics", "AI")
    SettingCategory.KUALITAS_MEDIA -> listOf("media_quality_section")
    SettingCategory.MODE_HEMAT_DATA -> listOf("data_saver_section")
    SettingCategory.PENYIMPANAN -> listOf("storage", "backup")
    SettingCategory.TENTANG_DUKUNGAN -> listOf("about_us")
}

@Composable
internal fun SettingCategory.label(): String = stringResource(when(this) {
    SettingCategory.AKUN -> Res.string.settings_category_account
    SettingCategory.KONTEN_TAMPILAN -> Res.string.settings_category_content_display
    SettingCategory.PRIVASI_SOSIAL -> Res.string.settings_category_privacy_social
    SettingCategory.KONEKSI -> Res.string.settings_category_network
    SettingCategory.PLAYBACK -> Res.string.settings_category_playback
    SettingCategory.LIRIK -> Res.string.settings_category_lyrics
    SettingCategory.KUALITAS_MEDIA -> Res.string.settings_category_media_quality
    SettingCategory.MODE_HEMAT_DATA -> Res.string.settings_category_data_saver
    SettingCategory.PENYIMPANAN -> Res.string.settings_category_storage_backup
    SettingCategory.TENTANG_DUKUNGAN -> Res.string.settings_category_about_support
})

@Composable
private fun SettingCategory.description(): String = stringResource(when(this) {
    SettingCategory.AKUN -> Res.string.settings_category_account_desc
    SettingCategory.KONTEN_TAMPILAN -> Res.string.settings_category_content_display_desc
    SettingCategory.PRIVASI_SOSIAL -> Res.string.settings_category_privacy_social_desc
    SettingCategory.KONEKSI -> Res.string.settings_category_network_desc
    SettingCategory.PLAYBACK -> Res.string.settings_category_playback_desc
    SettingCategory.LIRIK -> Res.string.settings_category_lyrics_desc
    SettingCategory.KUALITAS_MEDIA -> Res.string.settings_category_media_quality_desc
    SettingCategory.MODE_HEMAT_DATA -> Res.string.settings_category_data_saver_desc
    SettingCategory.PENYIMPANAN -> Res.string.settings_category_storage_backup_desc
    SettingCategory.TENTANG_DUKUNGAN -> Res.string.settings_category_about_support_desc
})

@Composable
fun SettingsHomeContent(query: String, onQuery: (String) -> Unit, onOpen: (SettingCategory, String?) -> Unit,
    modifier: Modifier = Modifier) {
    val categories = listOf(SettingCategory.AKUN, SettingCategory.PLAYBACK, SettingCategory.KUALITAS_MEDIA,
        SettingCategory.MODE_HEMAT_DATA, SettingCategory.LIRIK, SettingCategory.KONTEN_TAMPILAN,
        SettingCategory.PRIVASI_SOSIAL, SettingCategory.PENYIMPANAN, SettingCategory.KONEKSI, SettingCategory.TENTANG_DUKUNGAN)
    val titles = categories.associateWith { it.label() }
    val descriptions = categories.associateWith { it.description() }
    val entries = settingsSearchEntries.map { it to stringResource(it.title) }
    val term = query.trim()
    val matches = entries.filter { (entry, title) -> matchesSettingsSearch(term, title, titles.getValue(entry.category), entry.keywords) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.settings_home_hint), style = typo().bodyMedium, color = GratifyColors.TextSecondary)
        OutlinedTextField(query, onQuery, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text(stringResource(Res.string.settings_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Rounded.Close, stringResource(Res.string.settings_clear_search))
                }
            })
        if (term.isEmpty()) {
            Text(stringResource(Res.string.settings_browse), style = typo().titleMedium, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            categories.forEach { category -> SettingsNavigationRow(titles.getValue(category), descriptions.getValue(category)) { onOpen(category, null) } }
        } else if (matches.isEmpty()) {
            Text(stringResource(Res.string.settings_no_results), style = typo().titleMedium, modifier = Modifier.padding(top = 12.dp))
            Text(stringResource(Res.string.settings_no_results_hint), style = typo().bodyMedium, color = GratifyColors.TextSecondary)
        } else {
            Text(stringResource(Res.string.settings_search_results), style = typo().titleMedium, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            matches.forEach { (entry, title) -> SettingsNavigationRow(title, titles.getValue(entry.category)) { onOpen(entry.category, entry.section) } }
        }
    }
}

@Composable
private fun SettingsNavigationRow(title: String, description: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 76.dp).padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = typo().titleSmall, color = GratifyColors.TextPrimary, fontWeight = FontWeight.Medium)
            Text(description, style = typo().bodySmall, color = GratifyColors.TextSecondary)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = GratifyColors.TextSecondary)
    }
}
