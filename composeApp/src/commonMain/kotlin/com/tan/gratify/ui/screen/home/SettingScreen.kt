package com.tan.gratify.ui.screen.home
import gratify.composeapp.generated.resources.*
import com.tan.gratify.viewModel.SyncUiState
import gratify.composeapp.generated.resources.cloud_backup_title
import gratify.composeapp.generated.resources.cloud_backup_description
import gratify.composeapp.generated.resources.cloud_restore_title
import gratify.composeapp.generated.resources.cloud_restore_description
import gratify.composeapp.generated.resources.cloud_sync_working
import gratify.composeapp.generated.resources.cloud_sync_success
import gratify.composeapp.generated.resources.cloud_sync_failed

import androidx.compose.ui.text.style.TextOverflow

import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.ui.theme.GratifyShapes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.eygraber.uri.toKmpUri
import com.tan.common.LIMIT_CACHE_SIZE
import com.tan.common.QUALITY
import com.tan.common.SUPPORTED_LANGUAGE
import com.tan.common.SUPPORTED_LOCATION
import com.tan.common.SponsorBlockType
import com.tan.common.VIDEO_QUALITY
import com.tan.domain.extension.now
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.manager.DataStoreManager.Values.TRUE
import com.tan.domain.utils.LocalResource
import com.tan.logger.Logger
import com.tan.gratify.Platform
import com.tan.gratify.expect.ui.fileSaverResult
import com.tan.gratify.expect.ui.openEqResult
import com.tan.gratify.extension.bytesToMB
import com.tan.gratify.extension.displayString
import com.tan.gratify.extension.isTwoLetterCode
import com.tan.gratify.extension.isValidProxyHost
import com.tan.gratify.getPlatform
import com.tan.gratify.ui.component.ActionButton
import com.tan.gratify.ui.component.CenterLoadingBox
import com.tan.gratify.ui.component.EndOfPage
import com.tan.gratify.ui.component.RippleIconButton
import com.tan.gratify.ui.component.SettingItem
import com.tan.gratify.ui.navigation.destination.home.CreditDestination
import com.tan.gratify.ui.navigation.destination.login.LoginDestination
import com.tan.gratify.ui.theme.DarkColors
import com.tan.gratify.ui.theme.md_theme_dark_primary
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.ui.theme.white
import com.tan.gratify.utils.VersionManager
import com.tan.gratify.viewModel.SettingAlertState
import com.tan.gratify.viewModel.SettingBasicAlertState
import com.tan.gratify.viewModel.SettingsViewModel
import com.tan.gratify.viewModel.SharedViewModel

import com.mohamedrejeb.calf.core.ExperimentalCalfApi
import com.mohamedrejeb.calf.io.getPath
import com.mohamedrejeb.calf.picker.FilePickerFileType
import com.mohamedrejeb.calf.picker.FilePickerSelectionMode
import com.mohamedrejeb.calf.picker.rememberFilePickerLauncher
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import gratify.composeapp.generated.resources.playback_close_behavior
import gratify.composeapp.generated.resources.playback_close_behavior_description
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.settings_category_account
import gratify.composeapp.generated.resources.settings_category_account_desc
import gratify.composeapp.generated.resources.settings_category_content_display
import gratify.composeapp.generated.resources.settings_category_content_display_desc
import gratify.composeapp.generated.resources.settings_category_privacy_social
import gratify.composeapp.generated.resources.settings_category_privacy_social_desc
import gratify.composeapp.generated.resources.settings_category_playback
import gratify.composeapp.generated.resources.settings_category_playback_desc
import gratify.composeapp.generated.resources.settings_category_media_quality
import gratify.composeapp.generated.resources.settings_category_media_quality_desc
import gratify.composeapp.generated.resources.settings_category_data_saver
import gratify.composeapp.generated.resources.settings_category_data_saver_desc
import gratify.composeapp.generated.resources.settings_category_storage_backup
import gratify.composeapp.generated.resources.settings_category_storage_backup_desc
import gratify.composeapp.generated.resources.settings_category_about_support
import gratify.composeapp.generated.resources.settings_category_about_support_desc
import gratify.composeapp.generated.resources.about_us
import gratify.composeapp.generated.resources.instagram
import gratify.composeapp.generated.resources.baseline_signal_cellular_alt_24
import gratify.composeapp.generated.resources.outline_download_for_offline_24
import gratify.composeapp.generated.resources.baseline_settings_24
import gratify.composeapp.generated.resources.baseline_info_24
import gratify.composeapp.generated.resources.baseline_lyrics_24
import gratify.composeapp.generated.resources.baseline_play_circle_24
import gratify.composeapp.generated.resources.add_an_account
import gratify.composeapp.generated.resources.ai
import gratify.composeapp.generated.resources.ai_api_key
import gratify.composeapp.generated.resources.ai_provider
import gratify.composeapp.generated.resources.anonymous
import gratify.composeapp.generated.resources.app_name
import gratify.composeapp.generated.resources.audio
import gratify.composeapp.generated.resources.author
import gratify.composeapp.generated.resources.auto_backup
import gratify.composeapp.generated.resources.auto_backup_description
import gratify.composeapp.generated.resources.auto_check_for_update
import gratify.composeapp.generated.resources.auto_check_for_update_description
import gratify.composeapp.generated.resources.backup
import gratify.composeapp.generated.resources.backup_downloaded
import gratify.composeapp.generated.resources.backup_downloaded_description
import gratify.composeapp.generated.resources.backup_frequency
import gratify.composeapp.generated.resources.balance_media_loudness
import gratify.composeapp.generated.resources.baseline_arrow_back_ios_new_24
import gratify.composeapp.generated.resources.baseline_close_24
import gratify.composeapp.generated.resources.baseline_people_alt_24
import gratify.composeapp.generated.resources.baseline_playlist_add_24
import gratify.composeapp.generated.resources.better_lyrics
import gratify.composeapp.generated.resources.blur_fullscreen_lyrics
import gratify.composeapp.generated.resources.blur_fullscreen_lyrics_description
import gratify.composeapp.generated.resources.blur_player_background
import gratify.composeapp.generated.resources.blur_player_background_description
import gratify.composeapp.generated.resources.buy_me_a_coffee
import gratify.composeapp.generated.resources.cancel
import gratify.composeapp.generated.resources.canvas_info
import gratify.composeapp.generated.resources.categories_sponsor_block
import gratify.composeapp.generated.resources.change
import gratify.composeapp.generated.resources.change_language_warning
import gratify.composeapp.generated.resources.check_for_update
import gratify.composeapp.generated.resources.checking
import gratify.composeapp.generated.resources.clear
import gratify.composeapp.generated.resources.clear_canvas_cache
import gratify.composeapp.generated.resources.clear_downloaded_cache
import gratify.composeapp.generated.resources.clear_player_cache
import gratify.composeapp.generated.resources.clear_thumbnail_cache
import gratify.composeapp.generated.resources.content
import gratify.composeapp.generated.resources.content_country
import gratify.composeapp.generated.resources.contributor_email
import gratify.composeapp.generated.resources.contributor_name
import gratify.composeapp.generated.resources.crossfade
import gratify.composeapp.generated.resources.crossfade_auto
import gratify.composeapp.generated.resources.crossfade_description
import gratify.composeapp.generated.resources.crossfade_dj_mode
import gratify.composeapp.generated.resources.crossfade_dj_mode_description
import gratify.composeapp.generated.resources.crossfade_duration
import gratify.composeapp.generated.resources.custom_ai_model_id
import gratify.composeapp.generated.resources.custom_model_id_messages
import gratify.composeapp.generated.resources.daily
import gratify.composeapp.generated.resources.database
import gratify.composeapp.generated.resources.default_models
import gratify.composeapp.generated.resources.description_and_licenses
import gratify.composeapp.generated.resources.developer_blog
import gratify.composeapp.generated.resources.developer_blog_tagline
import gratify.composeapp.generated.resources.discord_integration
import gratify.composeapp.generated.resources.donation
import gratify.composeapp.generated.resources.download_quality
import gratify.composeapp.generated.resources.downloaded_cache
import gratify.composeapp.generated.resources.enable_canvas
import gratify.composeapp.generated.resources.enable_liquid_glass_effect
import gratify.composeapp.generated.resources.enable_liquid_glass_effect_description
import gratify.composeapp.generated.resources.enable_rich_presence
import gratify.composeapp.generated.resources.enable_sponsor_block
import gratify.composeapp.generated.resources.enable_spotify_lyrics
import gratify.composeapp.generated.resources.free_space
import gratify.composeapp.generated.resources.gemini
import gratify.composeapp.generated.resources.guest
import gratify.composeapp.generated.resources.help_build_lyrics_database
import gratify.composeapp.generated.resources.help_build_lyrics_database_description
import gratify.composeapp.generated.resources.http
import gratify.composeapp.generated.resources.intro_login_to_discord
import gratify.composeapp.generated.resources.intro_login_to_spotify
import gratify.composeapp.generated.resources.invalid
import gratify.composeapp.generated.resources.invalid_api_key
import gratify.composeapp.generated.resources.invalid_host
import gratify.composeapp.generated.resources.invalid_language_code
import gratify.composeapp.generated.resources.invalid_port
import gratify.composeapp.generated.resources.keep_backups
import gratify.composeapp.generated.resources.keep_backups_format
import gratify.composeapp.generated.resources.keep_your_youtube_playlist_offline
import gratify.composeapp.generated.resources.keep_your_youtube_playlist_offline_description
import gratify.composeapp.generated.resources.language
import gratify.composeapp.generated.resources.last_backup
import gratify.composeapp.generated.resources.last_checked_at
import gratify.composeapp.generated.resources.never
import gratify.composeapp.generated.resources.limit_player_cache
import gratify.composeapp.generated.resources.local_tracking_description
import gratify.composeapp.generated.resources.local_tracking_title
import gratify.composeapp.generated.resources.log_in_to_discord
import gratify.composeapp.generated.resources.log_in_to_spotify
import gratify.composeapp.generated.resources.log_out
import gratify.composeapp.generated.resources.log_out_warning
import gratify.composeapp.generated.resources.logged_in
import gratify.composeapp.generated.resources.lrclib
import gratify.composeapp.generated.resources.lyrics
import gratify.composeapp.generated.resources.lyrics_animation
import gratify.composeapp.generated.resources.lyrics_no_animation
import gratify.composeapp.generated.resources.main_lyrics_provider
import gratify.composeapp.generated.resources.manage_your_youtube_accounts
import gratify.composeapp.generated.resources.tan_dev
import gratify.composeapp.generated.resources.monthly
import gratify.composeapp.generated.resources.never
import gratify.composeapp.generated.resources.no_account
import gratify.composeapp.generated.resources.normalize_volume
import gratify.composeapp.generated.resources.open_system_equalizer
import gratify.composeapp.generated.resources.openai
import gratify.composeapp.generated.resources.openai_api_compatible
import gratify.composeapp.generated.resources.other_app
import gratify.composeapp.generated.resources.play_explicit_content
import gratify.composeapp.generated.resources.play_explicit_content_description
import gratify.composeapp.generated.resources.play_video_for_video_track_instead_of_audio_only
import gratify.composeapp.generated.resources.playback
import gratify.composeapp.generated.resources.player_cache
import gratify.composeapp.generated.resources.proxy
import gratify.composeapp.generated.resources.proxy_description
import gratify.composeapp.generated.resources.proxy_host
import gratify.composeapp.generated.resources.proxy_host_message
import gratify.composeapp.generated.resources.proxy_password
import gratify.composeapp.generated.resources.proxy_password_message
import gratify.composeapp.generated.resources.proxy_port
import gratify.composeapp.generated.resources.proxy_port_message
import gratify.composeapp.generated.resources.proxy_type
import gratify.composeapp.generated.resources.proxy_username
import gratify.composeapp.generated.resources.proxy_username_message
import gratify.composeapp.generated.resources.quality
import gratify.composeapp.generated.resources.restore_your_data
import gratify.composeapp.generated.resources.restore_your_saved_data
import gratify.composeapp.generated.resources.rich_presence_info
import gratify.composeapp.generated.resources.save
import gratify.composeapp.generated.resources.save_all_your_playlist_data
import gratify.composeapp.generated.resources.save_last_played
import gratify.composeapp.generated.resources.save_last_played_track_and_queue
import gratify.composeapp.generated.resources.save_playback_state
import gratify.composeapp.generated.resources.save_shuffle_and_repeat_mode
import gratify.composeapp.generated.resources.send_back_listening_data_to_google
import gratify.composeapp.generated.resources.set
import gratify.composeapp.generated.resources.settings
import gratify.composeapp.generated.resources.signed_in
import gratify.composeapp.generated.resources.gratify_lyrics
import gratify.composeapp.generated.resources.simp_music_lyrics
import gratify.composeapp.generated.resources.skip_no_music_part
import gratify.composeapp.generated.resources.skip_silent
import gratify.composeapp.generated.resources.skip_sponsor_part_of_video
import gratify.composeapp.generated.resources.socks
import gratify.composeapp.generated.resources.sponsorBlock
import gratify.composeapp.generated.resources.sponsor_block_intro
import gratify.composeapp.generated.resources.spotify
import gratify.composeapp.generated.resources.spotify_canvas_cache
import gratify.composeapp.generated.resources.spotify_lyrícs_info
import gratify.composeapp.generated.resources.storage
import gratify.composeapp.generated.resources.such_as_music_video_lyrics_video_podcasts_and_more

import gratify.composeapp.generated.resources.thumbnail_cache
import gratify.composeapp.generated.resources.translation_language
import gratify.composeapp.generated.resources.lyrics_translation_enabled
import gratify.composeapp.generated.resources.lyrics_translation_enabled_description
import gratify.composeapp.generated.resources.translation_language_message
import gratify.composeapp.generated.resources.translucent_bottom_navigation_bar
import gratify.composeapp.generated.resources.unknown
import gratify.composeapp.generated.resources.update_channel
import gratify.composeapp.generated.resources.upload_your_listening_history_to_youtube_music_server_it_will_make_yt_music_recommendation_system_better_working_only_if_logged_in
import gratify.composeapp.generated.resources.use_ai_translation
import gratify.composeapp.generated.resources.use_ai_translation_description
import gratify.composeapp.generated.resources.use_your_system_equalizer
import gratify.composeapp.generated.resources.user_interface
import gratify.composeapp.generated.resources.version
import gratify.composeapp.generated.resources.version_format
import gratify.composeapp.generated.resources.video_download_quality
import gratify.composeapp.generated.resources.video_quality
import gratify.composeapp.generated.resources.warning
import gratify.composeapp.generated.resources.weekly
import gratify.composeapp.generated.resources.what_segments_will_be_skipped
import gratify.composeapp.generated.resources.you_can_see_the_content_below_the_bottom_bar
import gratify.composeapp.generated.resources.youtube_account
import gratify.composeapp.generated.resources.youtube_subtitle_language
import gratify.composeapp.generated.resources.youtube_subtitle_language_message
import gratify.composeapp.generated.resources.youtube_transcript
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalCoilApi::class,
    ExperimentalHazeMaterialsApi::class,
    FormatStringsInDatetimeFormats::class,
    ExperimentalCalfApi::class,
)
@Composable
fun SettingScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinInject(),
) {
    val ui_settings_custom_url = stringResource(Res.string.settings_custom_url)
    val ui_settings_custom_url_hint = stringResource(Res.string.settings_custom_url_hint)
    val ui_settings_custom_headers = stringResource(Res.string.settings_custom_headers)
    val ui_settings_custom_headers_hint = stringResource(Res.string.settings_custom_headers_hint)
    val ui_settings_api_key_hint = stringResource(Res.string.settings_api_key_hint)
    val ui_ai_api_key = stringResource(Res.string.ai_api_key)
    val ui_ai_provider = stringResource(Res.string.ai_provider)
    val ui_backup_frequency = stringResource(Res.string.backup_frequency)
    val ui_better_lyrics = stringResource(Res.string.better_lyrics)
    val ui_cancel = stringResource(Res.string.cancel)
    val ui_categories_sponsor_block = stringResource(Res.string.categories_sponsor_block)
    val ui_change = stringResource(Res.string.change)
    val ui_change_language_warning = stringResource(Res.string.change_language_warning)
    val ui_checking = stringResource(Res.string.checking)
    val ui_clear = stringResource(Res.string.clear)
    val ui_clear_canvas_cache = stringResource(Res.string.clear_canvas_cache)
    val ui_download_remove_hint = stringResource(Res.string.settings_remove_downloads_hint)
    val ui_restore_confirm = stringResource(Res.string.settings_restore_confirm)
    val ui_restore_confirm_hint = stringResource(Res.string.settings_restore_confirm_hint)
    val ui_clear_downloaded_cache = stringResource(Res.string.clear_downloaded_cache)
    val ui_clear_player_cache = stringResource(Res.string.clear_player_cache)
    val ui_clear_thumbnail_cache = stringResource(Res.string.clear_thumbnail_cache)
    val ui_content_country = stringResource(Res.string.content_country)
    val ui_contributor_email = stringResource(Res.string.contributor_email)
    val ui_contributor_name = stringResource(Res.string.contributor_name)
    val ui_crossfade_auto = stringResource(Res.string.crossfade_auto)
    val ui_crossfade_duration = stringResource(Res.string.crossfade_duration)
    val ui_custom_ai_model_id = stringResource(Res.string.custom_ai_model_id)
    val ui_custom_model_id_messages = stringResource(Res.string.custom_model_id_messages)
    val ui_daily = stringResource(Res.string.daily)
    val ui_download_quality = stringResource(Res.string.download_quality)
    val ui_gemini = stringResource(Res.string.gemini)
    val ui_simp_music_lyrics = stringResource(Res.string.simp_music_lyrics)
    val ui_invalid = stringResource(Res.string.invalid)
    val ui_invalid_api_key = stringResource(Res.string.invalid_api_key)
    val ui_invalid_host = stringResource(Res.string.invalid_host)
    val ui_invalid_language_code = stringResource(Res.string.invalid_language_code)
    val ui_invalid_port = stringResource(Res.string.invalid_port)
    val ui_keep_backups = stringResource(Res.string.keep_backups)
    val ui_language = stringResource(Res.string.language)
    val ui_limit_player_cache = stringResource(Res.string.limit_player_cache)
    val ui_log_out = stringResource(Res.string.log_out)
    val ui_log_out_warning = stringResource(Res.string.log_out_warning)
    val ui_lrclib = stringResource(Res.string.lrclib)
    val ui_main_lyrics_provider = stringResource(Res.string.main_lyrics_provider)
    val ui_monthly = stringResource(Res.string.monthly)
    val ui_never = stringResource(Res.string.never)
    val ui_openai = stringResource(Res.string.openai)
    val ui_openai_api_compatible = stringResource(Res.string.openai_api_compatible)
    val ui_proxy_host = stringResource(Res.string.proxy_host)
    val ui_proxy_host_message = stringResource(Res.string.proxy_host_message)
    val ui_proxy_password = stringResource(Res.string.proxy_password)
    val ui_proxy_password_message = stringResource(Res.string.proxy_password_message)
    val ui_proxy_port = stringResource(Res.string.proxy_port)
    val ui_proxy_port_message = stringResource(Res.string.proxy_port_message)
    val ui_proxy_type = stringResource(Res.string.proxy_type)
    val ui_proxy_username = stringResource(Res.string.proxy_username)
    val ui_proxy_username_message = stringResource(Res.string.proxy_username_message)
    val ui_quality = stringResource(Res.string.quality)
    val ui_save = stringResource(Res.string.save)
    val ui_set = stringResource(Res.string.set)
    val ui_socks = stringResource(Res.string.socks)
    val ui_translation_language = stringResource(Res.string.translation_language)
    val ui_translation_language_message = stringResource(Res.string.translation_language_message)
    val ui_video_download_quality = stringResource(Res.string.video_download_quality)
    val ui_video_quality = stringResource(Res.string.video_quality)
    val ui_warning = stringResource(Res.string.warning)
    val ui_weekly = stringResource(Res.string.weekly)
    val ui_youtube_subtitle_language = stringResource(Res.string.youtube_subtitle_language)
    val ui_youtube_subtitle_language_message = stringResource(Res.string.youtube_subtitle_language_message)
    val ui_youtube_transcript = stringResource(Res.string.youtube_transcript)

    val platformContext = LocalPlatformContext.current
    val pl = com.mohamedrejeb.calf.core.LocalPlatformContext.current
    val localDensity = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    var width by rememberSaveable { mutableIntStateOf(0) }
    var activeCategory by rememberSaveable { mutableStateOf<SettingCategory?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sectionTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var topBarHeight by remember { mutableStateOf(64.dp) }
    val homeListState = rememberLazyListState()
    val detailListState = rememberSaveable(activeCategory, saver = LazyListState.Saver) { LazyListState() }
    val focusManager = LocalFocusManager.current
    val accountCoordinator: com.tan.data.sync.AccountSessionCoordinator = koinInject()
    val accountAccess by accountCoordinator.access.collectAsStateWithLifecycle()
    val signedIn = accountAccess is com.tan.data.sync.AccountAccess.Ready
    val settingsWriteFailed by viewModel.settingsWriteFailed.collectAsStateWithLifecycle()
    val settingsReadFailed by viewModel.settingsReadFailed.collectAsStateWithLifecycle()
    LaunchedEffect(activeCategory, sectionTarget) {
        activeCategory?.let { category -> detailListState.scrollToItem(category.sections().indexOf(sectionTarget).coerceAtLeast(0)) }
    }
    com.tan.gratify.ui.component.LayerBackHandler(activeCategory != null) { activeCategory = null }

    // Backup and restore
    val formatter =
        LocalDateTime.Format {
            byUnicodePattern("yyyyMMddHHmmss")
        }
    val appName = stringResource(Res.string.app_name)

    val backupLauncher =
        fileSaverResult(
            "${appName}_${
                now().format(
                    formatter,
                )
            }.backup",
            "application/octet-stream",
        ) { uri ->
            uri?.let {
                viewModel.backup(it.toKmpUri())
            }
        }

    val restoreLauncher =
        rememberFilePickerLauncher(
            type =
                FilePickerFileType.All,
            selectionMode = FilePickerSelectionMode.Single,
        ) { file ->
            file.firstOrNull()?.getPath(pl)?.toKmpUri()?.let {
                viewModel.restore(it)
            }
        }

    // Open equalizer
    val resultLauncher = openEqResult(viewModel.getAudioSessionId())

    val language by viewModel.language.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val quality by viewModel.quality.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val videoDownloadQuality by viewModel.videoDownloadQuality.collectAsStateWithLifecycle()
    val keepYoutubePlaylistOffline by viewModel.keepYouTubePlaylistOffline.collectAsStateWithLifecycle()
    val localTrackingEnabled by viewModel.localTrackingEnabled.collectAsStateWithLifecycle(initialValue = false)
    val combineLocalAndYouTubeLiked by viewModel.combineLocalAndYouTubeLiked.collectAsStateWithLifecycle()
    val playVideo by viewModel.playVideoInsteadOfAudio.map { it == TRUE }.collectAsStateWithLifecycle(initialValue = false)
    val videoQuality by viewModel.videoQuality.collectAsStateWithLifecycle()
    val sendData by viewModel.sendBackToGoogle.map { it == TRUE }.collectAsStateWithLifecycle(initialValue = false)
    val normalizeVolume by viewModel.normalizeVolume.map { it == TRUE }.collectAsStateWithLifecycle(initialValue = false)
    val skipSilent by viewModel.skipSilent.map { it == TRUE }.collectAsStateWithLifecycle(initialValue = false)
    val savePlaybackState by viewModel.savedPlaybackState.map { it == TRUE }.collectAsStateWithLifecycle(initialValue = false)
    val saveLastPlayed by viewModel.saveRecentSongAndQueue.map { it == TRUE }.collectAsStateWithLifecycle(initialValue = false)
    val mainLyricsProvider by viewModel.mainLyricsProvider.collectAsStateWithLifecycle()
    val youtubeSubtitleLanguage by viewModel.youtubeSubtitleLanguage.collectAsStateWithLifecycle()
    val enableSponsorBlock by viewModel.sponsorBlockEnabled.map { it == TRUE }.collectAsStateWithLifecycle(initialValue = false)
    val skipSegments by viewModel.sponsorBlockCategories.collectAsStateWithLifecycle()
    val playerCache by viewModel.cacheSize.collectAsStateWithLifecycle()
    val downloadedCache by viewModel.downloadedCacheSize.collectAsStateWithLifecycle()
    val thumbnailCache by viewModel.thumbCacheSize.collectAsStateWithLifecycle()
    val canvasCache by viewModel.canvasCacheSize.collectAsStateWithLifecycle()
    val limitPlayerCache by viewModel.playerCacheLimit.collectAsStateWithLifecycle()
    val fraction by viewModel.fraction.collectAsStateWithLifecycle()
    val lastCheckUpdate by viewModel.lastCheckForUpdate.collectAsStateWithLifecycle()
    val explicitContentEnabled by viewModel.explicitContentEnabled.collectAsStateWithLifecycle()
    val usingProxy by viewModel.usingProxy.collectAsStateWithLifecycle()
    val proxyType by viewModel.proxyType.collectAsStateWithLifecycle()
    val proxyHost by viewModel.proxyHost.collectAsStateWithLifecycle()
    val proxyPort by viewModel.proxyPort.collectAsStateWithLifecycle()
    val proxyUsername by viewModel.proxyUsername.collectAsStateWithLifecycle()
    val proxyPassword by viewModel.proxyPassword.collectAsStateWithLifecycle()
    val autoCheckUpdate by viewModel.autoCheckUpdate.collectAsStateWithLifecycle()
    val blurFullscreenLyrics by viewModel.blurFullscreenLyrics.collectAsStateWithLifecycle()
    val blurPlayerBackground by viewModel.blurPlayerBackground.collectAsStateWithLifecycle()
    val aiProvider by viewModel.aiProvider.collectAsStateWithLifecycle()
    val isHasApiKey by viewModel.isHasApiKey.collectAsStateWithLifecycle()
    val useAITranslation by viewModel.useAITranslation.collectAsStateWithLifecycle()
    val useTranslation by viewModel.useTranslation.collectAsStateWithLifecycle()
    val translationLanguage by viewModel.translationLanguage.collectAsStateWithLifecycle()
    val customModelId by viewModel.customModelId.collectAsStateWithLifecycle()
    val customOpenAIBaseUrl by viewModel.customOpenAIBaseUrl.collectAsStateWithLifecycle()
    val customOpenAIHeaders by viewModel.customOpenAIHeaders.collectAsStateWithLifecycle()
    val helpBuildLyricsDatabase by viewModel.helpBuildLyricsDatabase.collectAsStateWithLifecycle()
    val contributor by viewModel.contributor.collectAsStateWithLifecycle()
    val backupDownloaded by viewModel.backupDownloaded.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val autoBackupFrequency by viewModel.autoBackupFrequency.collectAsStateWithLifecycle()
    val autoBackupMaxFiles by viewModel.autoBackupMaxFiles.collectAsStateWithLifecycle()
    val autoBackupLastTime by viewModel.autoBackupLastTime.collectAsStateWithLifecycle()
    val updateChannel by viewModel.updateChannel.collectAsStateWithLifecycle()
    val deletingAccount by viewModel.deletingAccount.collectAsStateWithLifecycle()
    val cloudSyncState by viewModel.syncState.collectAsStateWithLifecycle()

    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsStateWithLifecycle()
    val crossfadeDuration by viewModel.crossfadeDuration.collectAsStateWithLifecycle()
    val crossfadeDjMode by viewModel.crossfadeDjMode.collectAsStateWithLifecycle()

    val isCheckingUpdate by sharedViewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val apkUpdateState by com.tan.gratify.expect.updateInstallState().collectAsStateWithLifecycle()


    val lastChecked = lastCheckUpdate?.toLongOrNull() ?: 0L
    val checkForUpdateSubtitle = when {
        apkUpdateState.stage != com.tan.gratify.expect.UpdateStage.IDLE -> when (apkUpdateState.stage) {
            com.tan.gratify.expect.UpdateStage.DOWNLOADING -> stringResource(Res.string.settings_update_downloading)
            com.tan.gratify.expect.UpdateStage.PREPARING -> stringResource(Res.string.settings_update_preparing)
            com.tan.gratify.expect.UpdateStage.APPLYING -> stringResource(Res.string.settings_update_applying)
            com.tan.gratify.expect.UpdateStage.VERIFYING -> stringResource(Res.string.settings_update_verifying)
            com.tan.gratify.expect.UpdateStage.ERROR -> stringResource(Res.string.settings_update_failed)
            else -> stringResource(Res.string.settings_update_ready)
        }
        isCheckingUpdate -> ui_checking
        lastChecked == 0L -> ui_never
        else -> stringResource(Res.string.last_checked_at,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(lastChecked)))
    }
    LaunchedEffect(true) {
        viewModel.getData()
        viewModel.getThumbCacheSize(platformContext)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
        state = if (activeCategory == null) homeListState else detailListState,
        contentPadding = PaddingValues(top = topBarHeight + innerPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding() + 24.dp),
        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize().imePadding().padding(horizontal = 16.dp),
    ) {
        if (activeCategory == null) item(key = "categories_list") {
            SettingsHomeContent(searchQuery, { searchQuery = it }, { category, section ->
                focusManager.clearFocus(); sectionTarget = section; activeCategory = category
            }, Modifier.padding(top = 16.dp))
        }
        if (activeCategory == SettingCategory.KONTEN_TAMPILAN) {
            item(key = "user_interface") {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text(text = stringResource(Res.string.user_interface), style = typo().labelMedium, color = white)
                    SettingItem(
                        title = stringResource(Res.string.blur_fullscreen_lyrics),
                        subtitle = stringResource(Res.string.blur_fullscreen_lyrics_description),
                        smallSubtitle = true,
                        switch = (blurFullscreenLyrics to { viewModel.setBlurFullscreenLyrics(it) }),
                    )
                    SettingItem(
                        title = stringResource(Res.string.blur_player_background),
                        subtitle = stringResource(Res.string.blur_player_background_description),
                        smallSubtitle = true,
                        switch = (blurPlayerBackground to { viewModel.setBlurPlayerBackground(it) }),
                    )

                }
            }
        }
        if (activeCategory == SettingCategory.AKUN) {
            item(key = "youtube_account_section") {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.youtube_account),
                        style = typo().labelMedium,
                        color = white,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    SettingItem(
                        title = stringResource(Res.string.youtube_account),
                        subtitle = stringResource(Res.string.settings_youtube_hint),
                        onClick = {
                            navController.navigate(LoginDestination)
                        },
                    )
                }
            }
        }

        if (activeCategory == SettingCategory.KONTEN_TAMPILAN) {
            item(key = "content_display_section") {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.content),
                        style = typo().labelMedium,
                        color = white,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    SettingItem(
                        title = stringResource(Res.string.language),
                        subtitle = SUPPORTED_LANGUAGE.getLanguageFromCode(language ?: "en-US"),
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = ui_language,
                                    selectOne =
                                        SettingAlertState.SelectData(
                                            listSelect =
                                                SUPPORTED_LANGUAGE.items.map {
                                                    (it.toString() == SUPPORTED_LANGUAGE.getLanguageFromCode(language ?: "en-US")) to it.toString()
                                                },
                                        ),
                                    confirm =
                                        ui_change to { state ->
                                            val code = SUPPORTED_LANGUAGE.getCodeFromLanguage(state.selectOne?.getSelected() ?: "English")
                                            viewModel.setBasicAlertData(
                                                SettingBasicAlertState(
                                                    title = ui_warning,
                                                    message = ui_change_language_warning,
                                                    confirm =
                                                        ui_change to {
                                                            viewModel.changeLanguage(code)
                                                            viewModel.setBasicAlertData(null)
                                                        },
                                                    dismiss = ui_cancel,
                                                ),
                                            )
                                        },
                                    dismiss = ui_cancel,
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.content_country),
                        subtitle = location ?: "",
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = ui_content_country,
                                    selectOne =
                                        SettingAlertState.SelectData(
                                            listSelect =
                                                SUPPORTED_LOCATION.items.map { item ->
                                                    (item.toString() == location) to item.toString()
                                                },
                                        ),
                                    confirm =
                                        ui_change to { state ->
                                            viewModel.changeLocation(
                                                state.selectOne?.getSelected() ?: "US",
                                            )
                                        },
                                    dismiss = ui_cancel,
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.play_explicit_content),
                        subtitle = stringResource(Res.string.play_explicit_content_description),
                        switch = (explicitContentEnabled to { viewModel.setExplicitContentEnabled(it) }),
                    )
                }
            }
        }

        if (activeCategory == SettingCategory.KUALITAS_MEDIA) {
            item(key = "media_quality_section") {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.settings_category_media_quality),
                        style = typo().labelMedium,
                        color = white,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    SettingItem(
                        title = stringResource(Res.string.quality),
                        subtitle = quality ?: "",
                        smallSubtitle = true,
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = ui_quality,
                                    selectOne =
                                        SettingAlertState.SelectData(
                                            listSelect =
                                                QUALITY.items.map { item ->
                                                    (item.toString() == quality) to item.toString()
                                                },
                                        ),
                                    confirm =
                                        ui_change to { state ->
                                            viewModel.changeQuality(state.selectOne?.getSelected())
                                        },
                                    dismiss = ui_cancel,
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.video_quality),
                        subtitle = videoQuality ?: "",
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = ui_video_quality,
                                    selectOne =
                                        SettingAlertState.SelectData(
                                            listSelect =
                                                VIDEO_QUALITY.items.map { item ->
                                                    (item.toString() == videoQuality) to item.toString()
                                                },
                                        ),
                                    confirm =
                                        ui_change to { state ->
                                            viewModel.changeVideoQuality(state.selectOne?.getSelected() ?: "")
                                        },
                                    dismiss = ui_cancel,
                                ),
                            )
                        },
                    )
                }
            }
        }

        if (activeCategory == SettingCategory.MODE_HEMAT_DATA) {
            item(key = "data_saver_section") {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.settings_category_data_saver),
                        style = typo().labelMedium,
                        color = white,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    SettingItem(
                        title = stringResource(Res.string.keep_your_youtube_playlist_offline),
                        subtitle = stringResource(Res.string.keep_your_youtube_playlist_offline_description),
                        switch = (keepYoutubePlaylistOffline to { viewModel.setKeepYouTubePlaylistOffline(it) }),
                    )
                    SettingItem(
                        title = stringResource(Res.string.download_quality),
                        subtitle = downloadQuality ?: "",
                        smallSubtitle = true,
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = ui_download_quality,
                                    selectOne =
                                        SettingAlertState.SelectData(
                                            listSelect =
                                                QUALITY.items.map { item ->
                                                    (item.toString() == downloadQuality) to item.toString()
                                                },
                                        ),
                                    confirm =
                                        ui_change to { state ->
                                            state.selectOne?.getSelected()?.let { viewModel.setDownloadQuality(it) }
                                        },
                                    dismiss = ui_cancel,
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.video_download_quality),
                        subtitle = videoDownloadQuality ?: "",
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = ui_video_download_quality,
                                    selectOne =
                                        SettingAlertState.SelectData(
                                            listSelect =
                                                VIDEO_QUALITY.items.map { item ->
                                                    (item.toString() == videoDownloadQuality) to item.toString()
                                                },
                                        ),
                                    confirm =
                                        ui_change to { state ->
                                            viewModel.setVideoDownloadQuality(state.selectOne?.getSelected() ?: "")
                                        },
                                    dismiss = ui_cancel,
                                ),
                            )
                        },
                    )
                }
            }
        }

        if (activeCategory == SettingCategory.PLAYBACK) {
            item(key = "playback_video_track_section") {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.settings_video_section),
                        style = typo().labelMedium,
                        color = white,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    SettingItem(
                        title = stringResource(Res.string.play_video_for_video_track_instead_of_audio_only),
                        subtitle = stringResource(Res.string.such_as_music_video_lyrics_video_podcasts_and_more),
                        smallSubtitle = true,
                        switch = (playVideo to { viewModel.setPlayVideoInsteadOfAudio(it) }),
                    )
                }
            }
        }

        if (activeCategory == SettingCategory.PRIVASI_SOSIAL) {
            item(key = "privacy_social_section") {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.settings_category_privacy_social),
                        style = typo().labelMedium,
                        color = white,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    SettingItem(
                        title = stringResource(Res.string.send_back_listening_data_to_google),
                        subtitle =
                            stringResource(
                                Res.string
                                    .upload_your_listening_history_to_youtube_music_server_it_will_make_yt_music_recommendation_system_better_working_only_if_logged_in,
                            ),
                        smallSubtitle = true,
                        switch = (sendData to { viewModel.setSendBackToGoogle(it) }),
                    )
                    SettingItem(
                        title = stringResource(Res.string.local_tracking_title),
                        subtitle = stringResource(Res.string.local_tracking_description),
                        switch = (localTrackingEnabled to { viewModel.setLocalTrackingEnabled(it) }),
                    )
                }
            }
        }
        if (activeCategory == SettingCategory.KONEKSI) item(key = "network") {
            Column {
                Text(stringResource(Res.string.settings_category_network_desc), style = typo().bodySmall,
                    color = GratifyColors.TextSecondary, modifier = Modifier.padding(vertical = 16.dp))
                SettingItem(title = stringResource(Res.string.proxy), subtitle = stringResource(Res.string.proxy_description),
                    switch = usingProxy to viewModel::setUsingProxy)
            }
        }
        if (activeCategory == SettingCategory.KONEKSI) {
            item(key = "proxy") {
                Crossfade(usingProxy) { enabled ->
                    if (enabled) Column {
                        SettingItem(
                            title = stringResource(Res.string.proxy_type),
                            subtitle = if (proxyType == DataStoreManager.ProxyType.PROXY_TYPE_SOCKS) ui_socks else "HTTP",
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = ui_proxy_type,
                                        selectOne =
                                            SettingAlertState.SelectData(
                                                listSelect =
                                                    listOf(
                                                        (proxyType == DataStoreManager.ProxyType.PROXY_TYPE_HTTP) to
                                                            runBlocking {
                                                                getString(
                                                                    Res.string.http,
                                                                )
                                                            },
                                                        (proxyType == DataStoreManager.ProxyType.PROXY_TYPE_SOCKS) to
                                                            ui_socks,
                                                    ),
                                            ),
                                        confirm =
                                            ui_change to { state ->
                                                viewModel.setProxy(
                                                    if (state.selectOne?.getSelected() == ui_socks) {
                                                        DataStoreManager.ProxyType.PROXY_TYPE_SOCKS
                                                    } else {
                                                        DataStoreManager.ProxyType.PROXY_TYPE_HTTP
                                                    },
                                                    proxyHost,
                                                    proxyPort,
                                                )
                                            },
                                        dismiss = ui_cancel,
                                    ),
                                )
                            },
                        )
                        SettingItem(
                            title = stringResource(Res.string.proxy_host),
                            subtitle = proxyHost,
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = ui_proxy_host,
                                        message = ui_proxy_host_message,
                                        textField =
                                            SettingAlertState.TextFieldData(
                                                label = ui_proxy_host,
                                                value = proxyHost,
                                                verifyCodeBlock = {
                                                    isValidProxyHost(it) to ui_invalid_host
                                                },
                                            ),
                                        confirm =
                                            ui_change to { state ->
                                                viewModel.setProxy(
                                                    proxyType,
                                                    state.textField?.value ?: "",
                                                    proxyPort,
                                                )
                                            },
                                        dismiss = ui_cancel,
                                    ),
                                )
                            },
                        )
                        SettingItem(
                            title = stringResource(Res.string.proxy_port),
                            subtitle = proxyPort.toString(),
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = ui_proxy_port,
                                        message = ui_proxy_port_message,
                                        textField =
                                            SettingAlertState.TextFieldData(
                                                label = ui_proxy_port,
                                                value = proxyPort.toString(),
                                                verifyCodeBlock = {
                                                    (it.toIntOrNull()?.let { port -> port in 1..65535 } == true) to ui_invalid_port
                                                },
                                            ),
                                        confirm =
                                            ui_change to { state ->
                                                viewModel.setProxy(
                                                    proxyType,
                                                    proxyHost,
                                                    state.textField?.value?.toIntOrNull() ?: 0,
                                                )
                                            },
                                        dismiss = ui_cancel,
                                    ),
                                )
                            },
                        )
                        SettingItem(
                            title = stringResource(Res.string.proxy_username),
                            subtitle = proxyUsername,
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = ui_proxy_username,
                                        message = ui_proxy_username_message,
                                        textField =
                                            SettingAlertState.TextFieldData(
                                                label = ui_proxy_username,
                                                value = proxyUsername,
                                            ),
                                        confirm =
                                            ui_change to { state ->
                                                viewModel.setProxyCredentials(
                                                    state.textField?.value ?: "",
                                                    proxyPassword,
                                                )
                                            },
                                        dismiss = ui_cancel,
                                    ),
                                )
                            },
                        )
                        SettingItem(
                            title = stringResource(Res.string.proxy_password),
                            subtitle =
                                if (proxyPassword.isEmpty()) {
                                    ""
                                } else {
                                    "\u2022".repeat(proxyPassword.length)
                                },
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = ui_proxy_password,
                                        message = ui_proxy_password_message,
                                        textField =
                                            SettingAlertState.TextFieldData(
                                                label = ui_proxy_password,
                                        sensitive = true,
                                                value = proxyPassword,
                                            ),
                                        confirm =
                                            ui_change to { state ->
                                                viewModel.setProxyCredentials(
                                                    proxyUsername,
                                                    state.textField?.value ?: "",
                                                )
                                            },
                                        dismiss = ui_cancel,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
        if (activeCategory == SettingCategory.PLAYBACK) {
            item(key = "audio") {
                Column {
                    Text(
                        text = stringResource(Res.string.audio),
                        style = typo().labelMedium,
                        color = white,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    SettingItem(
                        title = stringResource(Res.string.normalize_volume),
                        subtitle = stringResource(Res.string.balance_media_loudness),
                        switch = (normalizeVolume to { viewModel.setNormalizeVolume(it) }),
                    )
                    SettingItem(
                        title = stringResource(Res.string.skip_silent),
                        subtitle = stringResource(Res.string.skip_no_music_part),
                        switch = (skipSilent to { viewModel.setSkipSilent(it) }),
                    )
                    if (getPlatform() == Platform.Android) {
                        SettingItem(
                            title = stringResource(Res.string.open_system_equalizer),
                            subtitle = stringResource(Res.string.use_your_system_equalizer),
                            onClick = {
                                coroutineScope.launch {
                                    resultLauncher.launch()
                                }
                            },
                        )
                    }
                }
            }
        }
        if (activeCategory == SettingCategory.PLAYBACK) {
            item(key = "playback") {
                Column {
                Text(
                    text = stringResource(Res.string.playback),
                    style = typo().labelMedium,
                    color = white,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                SettingItem(
                    title = stringResource(Res.string.save_playback_state),
                    subtitle = stringResource(Res.string.save_shuffle_and_repeat_mode),
                    switch = (savePlaybackState to { viewModel.setSavedPlaybackState(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.save_last_played),
                    subtitle = stringResource(Res.string.save_last_played_track_and_queue),
                    switch = (saveLastPlayed to { viewModel.setSaveLastPlayed(it) }),
                )
                if (getPlatform() == Platform.Android) {
                    SettingItem(
                        title = stringResource(Res.string.playback_close_behavior),
                        subtitle = stringResource(Res.string.playback_close_behavior_description),
                        smallSubtitle = true,
                    )
                }
            }
        }
        }
        // Crossfade Settings (all platforms)
        if (activeCategory == SettingCategory.PLAYBACK) {
            item(key = "crossfade_settings") {
            Column {
                SettingItem(
                    title = stringResource(Res.string.crossfade),
                    subtitle = stringResource(Res.string.crossfade_description),
                    smallSubtitle = true,
                    switch = (crossfadeEnabled to { viewModel.setCrossfadeEnabled(it) }),
                )
                AnimatedVisibility(visible = crossfadeEnabled) {
                    Column {
                        SettingItem(
                            title = stringResource(Res.string.crossfade_duration),
                            subtitle =
                                if (crossfadeDuration == DataStoreManager.CROSSFADE_DURATION_AUTO) {
                                    stringResource(Res.string.crossfade_auto)
                                } else {
                                    "${crossfadeDuration / 1000}s"
                                },
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = ui_crossfade_duration,
                                        selectOne =
                                            SettingAlertState.SelectData(
                                                listSelect =
                                                    listOf(
                                                        (crossfadeDuration == DataStoreManager.CROSSFADE_DURATION_AUTO) to
                                                            ui_crossfade_auto,
                                                        (crossfadeDuration == 1000) to "1s",
                                                        (crossfadeDuration == 2000) to "2s",
                                                        (crossfadeDuration == 3000) to "3s",
                                                        (crossfadeDuration == 5000) to "5s",
                                                        (crossfadeDuration == 8000) to "8s",
                                                        (crossfadeDuration == 10000) to "10s",
                                                        (crossfadeDuration == 12000) to "12s",
                                                        (crossfadeDuration == 15000) to "15s",
                                                        (crossfadeDuration == 20000) to "20s",
                                                        (crossfadeDuration == 30000) to "30s",
                                                    ),
                                            ),
                                        confirm =
                                            ui_change to { state ->
                                                val duration =
                                                    when (state.selectOne?.getSelected()) {
                                                        runBlocking {
                                                            getString(
                                                                Res.string.crossfade_auto,
                                                            )
                                                        },
                                                        -> DataStoreManager.CROSSFADE_DURATION_AUTO
                                                        "1s" -> 1000
                                                        "2s" -> 2000
                                                        "3s" -> 3000
                                                        "5s" -> 5000
                                                        "8s" -> 8000
                                                        "10s" -> 10000
                                                        "12s" -> 12000
                                                        "15s" -> 15000
                                                        "20s" -> 20000
                                                        "30s" -> 30000
                                                        else -> 5000
                                                    }
                                                viewModel.setCrossfadeDuration(duration)
                                            },
                                        dismiss = ui_cancel,
                                    ),
                                )
                            },
                        )
                        SettingItem(
                            title = stringResource(Res.string.crossfade_dj_mode),
                            subtitle = stringResource(Res.string.crossfade_dj_mode_description),
                            smallSubtitle = true,
                            switch = ((crossfadeDjMode) to { viewModel.setCrossfadeDjMode(it) }),
                        )
                    }
                }
            }
        }
        }
        if (activeCategory == SettingCategory.LIRIK) {
            item(key = "lyrics") {
                Column {
                Text(
                    text = stringResource(Res.string.lyrics),
                    style = typo().labelMedium,
                    color = white,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                SettingItem(
                    title = stringResource(Res.string.lyrics_animation),
                    subtitle = stringResource(Res.string.lyrics_no_animation),
                )
                SettingItem(
                    title = stringResource(Res.string.main_lyrics_provider),
                    subtitle =
                        when (mainLyricsProvider) {
                            DataStoreManager.SIMPMUSIC, DataStoreManager.GRATIFYMUSIC -> stringResource(Res.string.simp_music_lyrics)
                            DataStoreManager.YOUTUBE -> stringResource(Res.string.youtube_transcript)
                            DataStoreManager.LRCLIB -> stringResource(Res.string.lrclib)
                            DataStoreManager.BETTER_LYRICS -> stringResource(Res.string.better_lyrics)
                            else -> stringResource(Res.string.unknown)
                        },
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = ui_main_lyrics_provider,
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            listOf(
                                                (mainLyricsProvider == DataStoreManager.SIMPMUSIC || mainLyricsProvider == DataStoreManager.GRATIFYMUSIC) to
                                                    ui_simp_music_lyrics,
                                                (mainLyricsProvider == DataStoreManager.YOUTUBE) to
                                                    ui_youtube_transcript,
                                                (mainLyricsProvider == DataStoreManager.LRCLIB) to ui_lrclib,
                                                (mainLyricsProvider == DataStoreManager.BETTER_LYRICS) to
                                                    ui_better_lyrics,
                                            ),
                                    ),
                                confirm =
                                    ui_change to { state ->
                                        viewModel.setLyricsProvider(
                                            when (state.selectOne?.getSelected()) {
                                                ui_simp_music_lyrics -> DataStoreManager.SIMPMUSIC
                                                ui_youtube_transcript -> DataStoreManager.YOUTUBE
                                                ui_lrclib -> DataStoreManager.LRCLIB
                                                ui_better_lyrics -> DataStoreManager.BETTER_LYRICS
                                                else -> DataStoreManager.SIMPMUSIC
                                            },
                                        )
                                    },
                                dismiss = ui_cancel,
                            ),
                        )
                    },
                )

                SettingItem(
                    title = stringResource(Res.string.lyrics_translation_enabled),
                    subtitle = stringResource(Res.string.lyrics_translation_enabled_description),
                    switch = ((useTranslation == DataStoreManager.TRUE || useAITranslation) to viewModel::setUseTranslation),
                )
                SettingItem(
                    title = stringResource(Res.string.translation_language),
                    subtitle = translationLanguage ?: "",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = ui_translation_language,
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = ui_translation_language,
                                        value = translationLanguage ?: "",
                                        verifyCodeBlock = {
                                            (it.length == 2 && it.isTwoLetterCode()) to
                                                ui_invalid_language_code
                                        },
                                    ),
                                message = ui_translation_language_message,
                                confirm =
                                    ui_change to { state ->
                                        viewModel.setTranslationLanguage(state.textField?.value ?: "")
                                    },
                                dismiss = ui_cancel,
                            ),
                        )
                    },
                    isEnable = true,
                )
                SettingItem(
                    title = stringResource(Res.string.youtube_subtitle_language),
                    subtitle = youtubeSubtitleLanguage,
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = ui_youtube_subtitle_language,
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = ui_youtube_subtitle_language,
                                        value = youtubeSubtitleLanguage,
                                        verifyCodeBlock = {
                                            (it.length == 2 && it.isTwoLetterCode()) to
                                                ui_invalid_language_code
                                        },
                                    ),
                                message = ui_youtube_subtitle_language_message,
                                confirm =
                                    ui_change to { state ->
                                        viewModel.setYoutubeSubtitleLanguage(state.textField?.value ?: "")
                                    },
                                dismiss = ui_cancel,
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.help_build_lyrics_database),
                    subtitle = stringResource(Res.string.help_build_lyrics_database_description),
                    switch = (helpBuildLyricsDatabase to { viewModel.setHelpBuildLyricsDatabase(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.contributor_name),
                    subtitle = contributor.first.ifEmpty { stringResource(Res.string.anonymous) },
                    isEnable = helpBuildLyricsDatabase,
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = ui_contributor_name,
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = ui_contributor_name,
                                        value = "",
                                    ),
                                message = "",
                                confirm =
                                    ui_set to { state ->
                                        viewModel.setContributorName(state.textField?.value ?: "")
                                    },
                                dismiss = ui_cancel,
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.contributor_email),
                    subtitle = contributor.second.ifEmpty { stringResource(Res.string.anonymous) },
                    isEnable = helpBuildLyricsDatabase,
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = ui_contributor_email,
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = ui_contributor_email,
                                        value = "",
                                        verifyCodeBlock = {
                                            if (it.isNotEmpty()) {
                                                (it.contains("@")) to ui_invalid
                                            } else {
                                                true to ""
                                            }
                                        },
                                    ),
                                message = "",
                                confirm =
                                    ui_set to { state ->
                                        viewModel.setContributorEmail(state.textField?.value ?: "")
                                    },
                                dismiss = ui_cancel,
                            ),
                        )
                    },
                )
            }
        }
        }
        if (activeCategory == SettingCategory.LIRIK) {
            item(key = "AI") {
                Column {
                Text(text = stringResource(Res.string.ai), style = typo().labelMedium, color = white, modifier = Modifier.padding(vertical = 8.dp))
                SettingItem(
                    title = stringResource(Res.string.ai_provider),
                    subtitle =
                        when (aiProvider) {
                            DataStoreManager.AI_PROVIDER_OPENAI -> stringResource(Res.string.openai)
                            DataStoreManager.AI_PROVIDER_GEMINI -> stringResource(Res.string.gemini)
                            DataStoreManager.AI_PROVIDER_CUSTOM_OPENAI -> stringResource(Res.string.openai_api_compatible)
                            else -> stringResource(Res.string.unknown)
                        },
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = ui_ai_provider,
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            listOf(
                                                (aiProvider == DataStoreManager.AI_PROVIDER_OPENAI) to
                                                    ui_openai,
                                                (aiProvider == DataStoreManager.AI_PROVIDER_GEMINI) to
                                                    ui_gemini,
                                                (aiProvider == DataStoreManager.AI_PROVIDER_CUSTOM_OPENAI) to
                                                    ui_openai_api_compatible,
                                            ),
                                    ),
                                confirm =
                                    ui_change to { state ->
                                        viewModel.setAIProvider(
                                            when (state.selectOne?.getSelected()) {
                                                ui_openai -> DataStoreManager.AI_PROVIDER_OPENAI
                                                ui_gemini -> DataStoreManager.AI_PROVIDER_GEMINI
                                                runBlocking {
                                                    getString(
                                                        Res.string.openai_api_compatible,
                                                    )
                                                },
                                                -> DataStoreManager.AI_PROVIDER_CUSTOM_OPENAI

                                                else -> DataStoreManager.AI_PROVIDER_OPENAI
                                            },
                                        )
                                    },
                                dismiss = ui_cancel,
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.ai_api_key),
                    subtitle = stringResource(if (isHasApiKey) Res.string.settings_configured else Res.string.settings_not_set),
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = ui_ai_api_key,
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = ui_ai_api_key,
                                        sensitive = true,
                                        value = "",
                                        verifyCodeBlock = {
                                            true to null
                                        },
                                    ),
                                message = ui_settings_api_key_hint,
                                confirm =
                                    ui_set to { state ->
                                        viewModel.setAIApiKey(state.textField?.value ?: "")
                                    },
                                dismiss = ui_cancel,
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.custom_ai_model_id),
                    subtitle = customModelId.ifEmpty { stringResource(Res.string.default_models) },
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = ui_custom_ai_model_id,
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = ui_custom_ai_model_id,
                                        value = "",
                                        verifyCodeBlock = {
                                            (it.isNotEmpty() && !it.contains(" ")) to ui_invalid
                                        },
                                    ),
                                message = ui_custom_model_id_messages,
                                confirm =
                                    ui_set to { state ->
                                        viewModel.setCustomModelId(state.textField?.value ?: "")
                                    },
                                dismiss = ui_cancel,
                            ),
                        )
                    },
                )
                // Custom OpenAI Base URL - only show when Custom OpenAI is selected
                if (aiProvider == DataStoreManager.AI_PROVIDER_CUSTOM_OPENAI) {
                    SettingItem(
                        title = ui_settings_custom_url,
                        subtitle = customOpenAIBaseUrl.ifEmpty { "https://api.openai.com/v1/" },
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = ui_settings_custom_url,
                                    textField =
                                        SettingAlertState.TextFieldData(
                                            label = ui_settings_custom_url,
                                            value = customOpenAIBaseUrl,
                                            verifyCodeBlock = {
                                                validSettingsApiUrl(it) to ui_invalid
                                            },
                                        ),
                                    message = ui_settings_custom_url_hint,
                                    confirm =
                                        ui_set to { state ->
                                            viewModel.setCustomOpenAIBaseUrl(state.textField?.value ?: "")
                                        },
                                    dismiss = ui_cancel,
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = ui_settings_custom_headers,
                        subtitle = if (customOpenAIHeaders.isNotEmpty()) stringResource(Res.string.settings_configured) else stringResource(Res.string.settings_not_set),
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = ui_settings_custom_headers,
                                    textField =
                                        SettingAlertState.TextFieldData(
                                            label = ui_settings_custom_headers,
                                            value = customOpenAIHeaders,
                                            verifyCodeBlock = { input ->
                                                validSettingsHeaders(input) to ui_invalid
                                            },
                                        ),
                                    message = ui_settings_custom_headers_hint,
                                    confirm =
                                        ui_set to { state ->
                                            viewModel.setCustomOpenAIHeaders(state.textField?.value ?: "")
                                        },
                                    dismiss = ui_cancel,
                                ),
                            )
                        },
                    )
                }
                SettingItem(
                    title = stringResource(Res.string.use_ai_translation),
                    subtitle = stringResource(Res.string.use_ai_translation_description),
                    switch = (useAITranslation to { viewModel.setAITranslation(it) }),
                    isEnable = isHasApiKey,

                )
            }
        }
        }
        if (activeCategory == SettingCategory.AKUN) {
            item(key = "google_link") {
                if (signedIn) Column {
                    Text(
                        text = "Google",
                        style = typo().labelMedium,
                        color = white,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    SettingItem(
                        title = "Tautkan Akun Google",
                        subtitle = stringResource(Res.string.settings_google_link_hint),
                        onClick = {
                            viewModel.linkGoogleAccount()
                        },
                    )
                }
            }
        }
        if (activeCategory == SettingCategory.PLAYBACK) {
            item(key = "sponsor_block") {
                Column {
                Text(
                    text = stringResource(Res.string.sponsorBlock),
                    style = typo().labelMedium,
                    color = white,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                SettingItem(
                    title = stringResource(Res.string.enable_sponsor_block),
                    subtitle = stringResource(Res.string.skip_sponsor_part_of_video),
                    switch = (enableSponsorBlock to { viewModel.setSponsorBlockEnabled(it) }),
                )
                val listName =
                    SponsorBlockType.toList().map { it.displayString() }
                SettingItem(
                    title = stringResource(Res.string.categories_sponsor_block),
                    subtitle = stringResource(Res.string.what_segments_will_be_skipped),
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = ui_categories_sponsor_block,
                                multipleSelect =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            listName
                                                .mapIndexed { index, item ->
                                                    (
                                                        skipSegments?.contains(
                                                            SponsorBlockType.toList().getOrNull(index)?.value,
                                                        ) == true
                                                    ) to item
                                                }.also {
                                                    Logger.w("SettingScreen", "SettingAlertState: $skipSegments")
                                                    Logger.w("SettingScreen", "SettingAlertState: $it")
                                                },
                                    ),
                                confirm =
                                    ui_save to { state ->
                                        viewModel.setSponsorBlockCategories(
                                            state.multipleSelect
                                                ?.getListSelected()
                                                ?.map { selected ->
                                                    listName.indexOf(selected)
                                                }?.mapNotNull { s ->
                                                    SponsorBlockType.toList().getOrNull(s).let {
                                                        it?.value
                                                    }
                                                }?.toCollection(ArrayList()) ?: arrayListOf(),
                                        )
                                    },
                                dismiss = ui_cancel,
                            ),
                        )
                    },
                    isEnable = enableSponsorBlock,
                )
                val beforeUrl = stringResource(Res.string.sponsor_block_intro).substringBefore("https://sponsor.ajay.app/")
                val afterUrl = stringResource(Res.string.sponsor_block_intro).substringAfter("https://sponsor.ajay.app/")
                Text(
                    buildAnnotatedString {
                        append(beforeUrl)
                        withLink(
                            LinkAnnotation.Url(
                                "https://sponsor.ajay.app/",
                                TextLinkStyles(style = SpanStyle(color = md_theme_dark_primary)),
                            ),
                        ) {
                            append("https://sponsor.ajay.app/")
                        }
                        append(afterUrl)
                    },
                    style = typo().bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
        }
        if (activeCategory == SettingCategory.PENYIMPANAN) {
            item(key = "storage") {
                Column {
                    Text(
                        text = stringResource(Res.string.storage),
                        style = typo().labelMedium,
                        color = white,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    SettingItem(
                        title = stringResource(Res.string.player_cache),
                        subtitle = "${playerCache.bytesToMB()} MB",
                        onClick = {
                            viewModel.setBasicAlertData(
                                SettingBasicAlertState(
                                    title = ui_clear_player_cache,
                                    message = null,
                                    confirm =
                                        ui_clear to {
                                            viewModel.clearPlayerCache()
                                        },
                                    dismiss = ui_cancel,
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.downloaded_cache),
                        subtitle = "${downloadedCache.bytesToMB()} MB",
                        onClick = {
                            viewModel.setBasicAlertData(
                                SettingBasicAlertState(
                                    title = ui_clear_downloaded_cache,
                                    message = ui_download_remove_hint,
                                    confirm =
                                        ui_clear to {
                                            viewModel.clearDownloadedCache()
                                        },
                                    dismiss = ui_cancel,
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.thumbnail_cache),
                        subtitle = "${thumbnailCache.bytesToMB()} MB",
                        onClick = {
                            viewModel.setBasicAlertData(
                                SettingBasicAlertState(
                                    title = ui_clear_thumbnail_cache,
                                    message = null,
                                    confirm =
                                        ui_clear to {
                                            viewModel.clearThumbnailCache(platformContext)
                                        },
                                    dismiss = ui_cancel,
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.spotify_canvas_cache),
                        subtitle = "${canvasCache.bytesToMB()} MB",
                        onClick = {
                            viewModel.setBasicAlertData(
                                SettingBasicAlertState(
                                    title = ui_clear_canvas_cache,
                                    message = null,
                                    confirm =
                                        ui_clear to {
                                            viewModel.clearCanvasCache()
                                        },
                                    dismiss = ui_cancel,
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.limit_player_cache),
                        subtitle = LIMIT_CACHE_SIZE.getItemFromData(limitPlayerCache).toString(),
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = ui_limit_player_cache,
                                    selectOne =
                                        SettingAlertState.SelectData(
                                            listSelect =
                                                LIMIT_CACHE_SIZE.items.map { item ->
                                                    (item == LIMIT_CACHE_SIZE.getItemFromData(limitPlayerCache)) to item.toString()
                                                },
                                        ),
                                    confirm =
                                        ui_change to { state ->
                                            viewModel.setPlayerCacheLimit(
                                                LIMIT_CACHE_SIZE.getDataFromItem(state.selectOne?.getSelected()),
                                            )
                                        },
                                    dismiss = ui_cancel,
                                ),
                            )
                        },
                    )
                }
            }
        }
        if (activeCategory == SettingCategory.PENYIMPANAN) {
            item(key = "backup") {
                Column {
                val syncing = cloudSyncState is SyncUiState.InProgress
                if (!signedIn) Text(stringResource(Res.string.settings_cloud_sign_in), style = typo().bodySmall, color = GratifyColors.TextSecondary, modifier = Modifier.padding(16.dp))
                SettingItem(
                    title = stringResource(Res.string.cloud_backup_title),
                    subtitle = stringResource(if (syncing) Res.string.cloud_sync_working else Res.string.cloud_backup_description),
                    isEnable = !syncing && signedIn,
                    onClick = { viewModel.syncUpNow() },
                )
                SettingItem(
                    title = stringResource(Res.string.cloud_restore_title),
                    subtitle = stringResource(if (syncing) Res.string.cloud_sync_working else Res.string.cloud_restore_description),
                    isEnable = !syncing && signedIn,
                    onClick = { viewModel.syncDownNow() },
                )
                if (cloudSyncState is SyncUiState.Success || cloudSyncState is SyncUiState.Error) {
                    Text(
                        text = stringResource(if (cloudSyncState is SyncUiState.Success) Res.string.cloud_sync_success else Res.string.cloud_sync_failed),
                        color = if (cloudSyncState is SyncUiState.Error) GratifyColors.Error else GratifyColors.Accent,
                        style = typo().bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Text(
                    text = stringResource(Res.string.backup),
                    style = typo().labelMedium,
                    color = white,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                SettingItem(
                    title = stringResource(Res.string.backup_downloaded),
                    subtitle = stringResource(Res.string.backup_downloaded_description),
                    switch = (backupDownloaded to { viewModel.setBackupDownloaded(it) }),
                )
                // Auto Backup (Android only)
                if (getPlatform() == Platform.Android) {
                    SettingItem(
                        title = stringResource(Res.string.auto_backup),
                        subtitle = stringResource(Res.string.auto_backup_description),
                        switch = (autoBackupEnabled to { viewModel.setAutoBackupEnabled(it) }),
                    )
                    AnimatedVisibility(visible = autoBackupEnabled) {
                        Column {
                            SettingItem(
                                title = stringResource(Res.string.backup_frequency),
                                subtitle =
                                    when (autoBackupFrequency) {
                                        DataStoreManager.AUTO_BACKUP_FREQUENCY_DAILY -> stringResource(Res.string.daily)
                                        DataStoreManager.AUTO_BACKUP_FREQUENCY_WEEKLY -> stringResource(Res.string.weekly)
                                        DataStoreManager.AUTO_BACKUP_FREQUENCY_MONTHLY -> stringResource(Res.string.monthly)
                                        else -> stringResource(Res.string.daily)
                                    },
                                onClick = {
                                    viewModel.setAlertData(
                                        SettingAlertState(
                                            title = ui_backup_frequency,
                                            selectOne =
                                                SettingAlertState.SelectData(
                                                    listSelect =
                                                        listOf(
                                                            (autoBackupFrequency == DataStoreManager.AUTO_BACKUP_FREQUENCY_DAILY) to
                                                                ui_daily,
                                                            (autoBackupFrequency == DataStoreManager.AUTO_BACKUP_FREQUENCY_WEEKLY) to
                                                                ui_weekly,
                                                            (autoBackupFrequency == DataStoreManager.AUTO_BACKUP_FREQUENCY_MONTHLY) to
                                                                ui_monthly,
                                                        ),
                                                ),
                                            confirm =
                                                ui_change to { state ->
                                                    val frequency =
                                                        when (state.selectOne?.getSelected()) {
                                                            runBlocking {
                                                                getString(
                                                                    Res.string.daily,
                                                                )
                                                            },
                                                            -> DataStoreManager.AUTO_BACKUP_FREQUENCY_DAILY
                                                            runBlocking {
                                                                getString(
                                                                    Res.string.weekly,
                                                                )
                                                            },
                                                            -> DataStoreManager.AUTO_BACKUP_FREQUENCY_WEEKLY
                                                            runBlocking {
                                                                getString(
                                                                    Res.string.monthly,
                                                                )
                                                            },
                                                            -> DataStoreManager.AUTO_BACKUP_FREQUENCY_MONTHLY
                                                            else -> DataStoreManager.AUTO_BACKUP_FREQUENCY_DAILY
                                                        }
                                                    viewModel.setAutoBackupFrequency(frequency)
                                                },
                                            dismiss = ui_cancel,
                                        ),
                                    )
                                },
                            )
                            SettingItem(
                                title = stringResource(Res.string.keep_backups),
                                subtitle = stringResource(Res.string.keep_backups_format, "$autoBackupMaxFiles"),
                                onClick = {
                                    viewModel.setAlertData(
                                        SettingAlertState(
                                            title = ui_keep_backups,
                                            selectOne =
                                                SettingAlertState.SelectData(
                                                    listSelect =
                                                        listOf(
                                                            (autoBackupMaxFiles == 3) to "3",
                                                            (autoBackupMaxFiles == 5) to "5",
                                                            (autoBackupMaxFiles == 10) to "10",
                                                            (autoBackupMaxFiles == 15) to "15",
                                                        ),
                                                ),
                                            confirm =
                                                ui_change to { state ->
                                                    val maxFiles = state.selectOne?.getSelected()?.toIntOrNull() ?: 5
                                                    viewModel.setAutoBackupMaxFiles(maxFiles)
                                                },
                                            dismiss = ui_cancel,
                                        ),
                                    )
                                },
                            )
                            SettingItem(
                                title = stringResource(Res.string.last_backup),
                                subtitle =
                                    if (autoBackupLastTime == 0L) {
                                        stringResource(Res.string.never)
                                    } else {
                                        DateTimeFormatter
                                            .ofPattern("yyyy-MM-dd HH:mm:ss")
                                            .withZone(ZoneId.systemDefault())
                                            .format(Instant.ofEpochMilli(autoBackupLastTime))
                                    },
                            )
                        }
                    }
                }
                SettingItem(
                    title = stringResource(Res.string.backup),
                    subtitle = stringResource(Res.string.save_all_your_playlist_data),
                    onClick = {
                        coroutineScope.launch {
                            backupLauncher.launch()
                        }
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.restore_your_data),
                    subtitle = stringResource(Res.string.restore_your_saved_data),
                    onClick = {
                        coroutineScope.launch {
                            viewModel.setBasicAlertData(SettingBasicAlertState(ui_restore_confirm, ui_restore_confirm_hint,
                                ui_restore_confirm to { coroutineScope.launch { restoreLauncher.launch() } }, ui_cancel))
                        }
                    },
                )
            }
        }
        }
        if (activeCategory == SettingCategory.TENTANG_DUKUNGAN) {
            item(key = "about_us") {
                AboutSupportContent(
                    version = VersionManager.getVersionName(), build = VersionManager.getVersionCode(),
                    updateSubtitle = checkForUpdateSubtitle,
                    checkingUpdate = isCheckingUpdate && apkUpdateState.stage == com.tan.gratify.expect.UpdateStage.IDLE,
                    autoUpdate = autoCheckUpdate, onAutoUpdate = viewModel::setAutoCheckUpdate,
                    onCheckUpdate = {
                        if (apkUpdateState.stage != com.tan.gratify.expect.UpdateStage.IDLE) {
                            com.tan.gratify.expect.openUpdateStatus()
                        } else if (!isCheckingUpdate) {
                            sharedViewModel.checkForUpdate(isManual = true)
                        }
                    },
                    onDeveloper = { navController.navigate(CreditDestination) },
                    onOpenLink = uriHandler::openUri,
                    onCopyInfo = { info -> com.tan.gratify.expect.copyToClipboard(appName, info) },
                )
            }
        }
        if (activeCategory == SettingCategory.AKUN) item(key = "gratify_account") {
            Column(Modifier.padding(vertical = 16.dp)) {
                if (signedIn) {
                    Text(stringResource(Res.string.settings_account_ready), style = typo().bodySmall,
                        color = GratifyColors.TextSecondary, modifier = Modifier.padding(16.dp))
                    SettingItem(title = stringResource(Res.string.log_out), subtitle = stringResource(Res.string.settings_logout_hint),
                        isEnable = !deletingAccount, onClick = {
                            viewModel.setBasicAlertData(SettingBasicAlertState(ui_warning, ui_log_out_warning,
                                ui_log_out to { viewModel.logOutGratify() }, ui_cancel))
                        })
                    val deleteTitle = stringResource(Res.string.settings_delete_account)
                    val deletePrompt = stringResource(Res.string.settings_delete_prompt)
                    val deleteHint = stringResource(Res.string.settings_delete_hint)
                    SettingItem(title = if (deletingAccount) stringResource(Res.string.settings_deleting) else deleteTitle,
                        subtitle = deleteHint, smallSubtitle = true, isEnable = !deletingAccount, onClick = {
                            viewModel.setBasicAlertData(SettingBasicAlertState(deletePrompt, deleteHint,
                                deleteTitle to { viewModel.deleteGratifyAccount() }, ui_cancel))
                        })
                } else {
                    SettingItem(title = stringResource(Res.string.settings_sign_in),
                        subtitle = stringResource(Res.string.settings_signed_out_hint),
                        isEnable = accountAccess is com.tan.data.sync.AccountAccess.SignedOut,
                        onClick = { navController.navigate(com.tan.gratify.ui.navigation.destination.login.LoginLandingDestination) })
                }
            }
        }
        if (settingsWriteFailed) item(key = "settings-write-error") {
            Text(stringResource(Res.string.settings_write_failed), style = typo().bodyMedium, color = GratifyColors.Error, modifier = Modifier.padding(16.dp))
        }
        if (settingsReadFailed) item(key = "settings-read-error") {
            Text(stringResource(Res.string.settings_read_failed), style = typo().bodyMedium,
                color = GratifyColors.Error, modifier = Modifier.padding(16.dp))
            TextButton(onClick = { viewModel.getData(); viewModel.getThumbCacheSize(platformContext) }) { Text(stringResource(Res.string.retry)) }
        }
        if (activeCategory != null) {
            item(key = "end") {
                EndOfPage()
            }
        }
    }
    }
    val basisAlertData by viewModel.basicAlertData.collectAsStateWithLifecycle()
    if (basisAlertData != null) {
        val alertBasicState = basisAlertData ?: return
        AlertDialog(
            onDismissRequest = { viewModel.setBasicAlertData(null) },
            title = {
                Text(
                    text = alertBasicState.title,
                    style = typo().titleSmall,
                )
            },
            text = {
                if (alertBasicState.message != null) {
                    Text(text = alertBasicState.message)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        alertBasicState.confirm.second.invoke()
                        viewModel.setBasicAlertData(null)
                    },
                ) {
                    Text(text = alertBasicState.confirm.first)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setBasicAlertData(null)
                    },
                ) {
                    Text(text = alertBasicState.dismiss)
                }
            },
        )
    }
    val alertData by viewModel.alertData.collectAsStateWithLifecycle()
    if (alertData != null) {
        val alertState = alertData ?: return
        SettingsValueDialog(alertState, viewModel::setAlertData) { viewModel.setAlertData(null) }
    }

    TopAppBar(
        title = {
            Text(
                text = activeCategory?.label() ?: stringResource(Res.string.settings),
                style = typo().titleMedium,
            )
        },
        navigationIcon = {
            IconButton(onClick = { if (activeCategory != null) activeCategory = null else navController.navigateUp() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.ui_back))
            }
        },
        modifier = Modifier.onGloballyPositioned { topBarHeight = with(localDensity) { it.size.height.toDp() } },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
    )
}

enum class SettingCategory {
    AKUN,
    KONTEN_TAMPILAN,
    PRIVASI_SOSIAL,
    KONEKSI,
    LIRIK,
    PLAYBACK,
    KUALITAS_MEDIA,
    MODE_HEMAT_DATA,
    PENYIMPANAN,
    TENTANG_DUKUNGAN
}
