package com.tan.gratify.ui.screen.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.component.LegalDocumentLinks
import com.tan.gratify.ui.component.SettingItem
import com.tan.gratify.ui.theme.*
import gratify.composeapp.generated.resources.*
import io.ktor.http.encodeURLParameter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal const val GRATIFY_SUPPORT_EMAIL = "supportgratify@gmail.com"
internal fun aboutVersionInfo(version: String, build: Long): String = "Gratify v$version (build $build)"
internal fun aboutSupportUrl(version: String, build: Long): String =
    "mailto:$GRATIFY_SUPPORT_EMAIL?subject=Gratify%20support&body=${aboutVersionInfo(version, build).encodeURLParameter()}"

@Composable
internal fun AboutSupportContent(
    version: String, build: Long, updateSubtitle: String, checkingUpdate: Boolean, autoUpdate: Boolean,
    onAutoUpdate: (Boolean) -> Unit, onCheckUpdate: () -> Unit, onDeveloper: () -> Unit,
    onOpenLink: (String) -> Unit, onCopyInfo: (String) -> Unit, modifier: Modifier = Modifier,
) {
    var copyStatus by rememberSaveable { mutableStateOf<Boolean?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(Res.drawable.mono), null, Modifier.size(64.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Gratify", style = typo().headlineLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(Res.string.about_app_summary), style = typo().bodyMedium, color = GratifyColors.TextSecondary)
            }
        }
        AboutSectionTitle(stringResource(Res.string.about_application))
        AboutActionRow(stringResource(Res.string.version), "v$version · ${stringResource(Res.string.about_build, build)}",
            Icons.Rounded.Info, Icons.Rounded.ContentCopy) {
            copyStatus = runCatching { onCopyInfo(aboutVersionInfo(version, build)) }.isSuccess
        }
        if (copyStatus != null) Text(stringResource(if (copyStatus == true) Res.string.about_info_copied else Res.string.about_copy_failed),
            style = typo().bodySmall, color = if (copyStatus == true) GratifyColors.TextSecondary else GratifyColors.Error,
            modifier = Modifier.padding(horizontal = 16.dp))
        SettingItem(title = stringResource(Res.string.check_for_update), subtitle = updateSubtitle,
            smallSubtitle = true, isEnable = !checkingUpdate, onClick = onCheckUpdate)
        SettingItem(title = stringResource(Res.string.auto_check_for_update),
            subtitle = stringResource(Res.string.auto_check_for_update_description), smallSubtitle = true,
            switch = autoUpdate to onAutoUpdate)
        AboutSectionTitle(stringResource(Res.string.about_people_support))
        AboutActionRow(stringResource(Res.string.about_developer_credits), stringResource(Res.string.about_developer_hint),
            Icons.Rounded.Person, Icons.AutoMirrored.Rounded.KeyboardArrowRight, onDeveloper)
        AboutExternalAction(stringResource(Res.string.about_contact_support), GRATIFY_SUPPORT_EMAIL,
            Icons.Rounded.MailOutline, aboutSupportUrl(version, build), onOpenLink)
        Text(stringResource(Res.string.about_support_hint), style = typo().bodySmall, color = GratifyColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp))
        AboutSectionTitle(stringResource(Res.string.about_privacy_terms))
        LegalDocumentLinks(showContact = false)
    }
}

@Composable
internal fun DeveloperCreditsContent(onOpenLink: (String) -> Unit, onLicenses: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.about_developer_heading), style = typo().headlineLarge,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp).semantics { heading() })
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).background(GratifyColors.AccentContainer, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Text("T", style = typo().headlineLarge, color = GratifyColors.Accent, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.tan_dev), style = typo().titleLarge, fontWeight = FontWeight.SemiBold)
                Text("@TanDjendra", style = typo().bodyMedium, color = GratifyColors.TextSecondary)
                Text(stringResource(Res.string.about_developer_role), style = typo().bodySmall, color = GratifyColors.TextSecondary)
            }
        }
        AboutSectionTitle(stringResource(Res.string.about_connect))
        AboutExternalAction(stringResource(Res.string.about_developer_site), "tanheradhe.my.id", Icons.Rounded.Language,
            "https://tanheradhe.my.id", onOpenLink)
        AboutExternalAction("GitHub", "TanDjendra", Icons.Rounded.Code, "https://github.com/TanDjendra", onOpenLink)
        AboutExternalAction("Instagram", "@calestaan", Icons.Rounded.OpenInNew, "https://instagram.com/calestaan", onOpenLink)
        AboutSectionTitle(stringResource(Res.string.about_open_source))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Code, null, tint = GratifyColors.TextSecondary, modifier = Modifier.size(24.dp))
            Text(stringResource(Res.string.about_upstream_credit), style = typo().bodyMedium,
                color = GratifyColors.TextSecondary, modifier = Modifier.weight(1f))
        }
        AboutExternalAction(stringResource(Res.string.about_source_code), "TanDjendra / Gratify", Icons.Rounded.Code,
            "https://github.com/TanDjendra/Gratify", onOpenLink)
        AboutActionRow(stringResource(Res.string.about_library_licenses), stringResource(Res.string.about_library_hint),
            Icons.Rounded.Description, Icons.AutoMirrored.Rounded.KeyboardArrowRight, onLicenses)
        AboutExternalAction(stringResource(Res.string.about_project_license), "GPL-3.0", Icons.Rounded.Description,
            "https://github.com/TanDjendra/Gratify/blob/main/LICENSE", onOpenLink)
        AboutSectionTitle(stringResource(Res.string.about_support_development))
        AboutExternalAction("Saweria", "saweria.co/Tanindomie", Icons.Rounded.FavoriteBorder,
            "https://saweria.co/Tanindomie", onOpenLink)
        Text(stringResource(Res.string.about_donation_optional), style = typo().bodySmall, color = GratifyColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
}

@Composable
private fun AboutSectionTitle(title: String) {
    Text(title, style = typo().titleSmall, fontWeight = FontWeight.SemiBold, color = GratifyColors.TextSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp).semantics { heading() })
}

@Composable
internal fun AboutActionRow(title: String, subtitle: String, icon: ImageVector, trailing: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 72.dp)
        .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = GratifyColors.TextSecondary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = typo().titleSmall, color = GratifyColors.TextPrimary)
            Text(subtitle, style = typo().bodySmall, color = GratifyColors.TextSecondary)
        }
        Icon(trailing, null, tint = GratifyColors.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AboutExternalAction(title: String, subtitle: String, icon: ImageVector, url: String, onOpenLink: (String) -> Unit) {
    var failed by rememberSaveable(url) { mutableStateOf(false) }
    AboutActionRow(title, subtitle, icon, Icons.Rounded.OpenInNew) { failed = runCatching { onOpenLink(url) }.isFailure }
    if (failed) SelectionContainer {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.about_link_failed), style = typo().bodySmall, color = GratifyColors.Error)
            Text(if (url.startsWith("mailto:")) GRATIFY_SUPPORT_EMAIL else url, style = typo().bodySmall, color = GratifyColors.TextSecondary)
        }
    }
}
