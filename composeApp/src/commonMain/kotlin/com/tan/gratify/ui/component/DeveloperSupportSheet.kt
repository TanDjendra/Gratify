package com.tan.gratify.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal const val DEVELOPER_INSTAGRAM_URL = "https://instagram.com/calestaan"
internal const val DEVELOPER_GITHUB_URL = "https://github.com/TanDjendra"
private val SupportSurface = Color(0xFF181818)
private val SupportCream = Color(0xFFF6F4EA)
private val SupportMuted = Color(0xFFC4C2BB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperSupportSheet(onDismiss: (Boolean) -> Unit, onOpenLink: (String) -> Unit) {
    var optedOut by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { onDismiss(optedOut) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 480.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = SupportSurface,
        contentColor = SupportCream,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = null,
    ) {
        DeveloperSupportContent(optedOut, { optedOut = it }, { onDismiss(optedOut) }, onOpenLink,
            modifier = Modifier.fillMaxHeight(0.88f))
    }
}

/** Native controls remain separate from the illustration, including at large font sizes. */
@Composable
internal fun DeveloperSupportContent(
    optedOut: Boolean,
    onOptOut: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    var failedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    fun open(url: String) {
        try { onOpenLink(url); onDismiss() }
        catch (_: Exception) { failedUrl = url }
    }
    val body = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 22.sp)
    val label = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    Column(modifier.fillMaxWidth().fillMaxHeight().background(SupportSurface)) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(32.dp, 4.dp)
                .background(Color(0xFF676767), CircleShape))
            IconButton(onDismiss, Modifier.align(Alignment.TopEnd).padding(end = 8.dp).size(48.dp)) {
                Icon(Icons.Rounded.Close, stringResource(Res.string.support_prompt_close), tint = SupportCream)
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            Image(painterResource(Res.drawable.support_organic_hero), null,
                modifier = Modifier.fillMaxWidth().height(if (largeText) 150.dp else 230.dp), contentScale = ContentScale.Crop)
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(Res.string.support_prompt_title), color = SupportCream,
                    style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = if (largeText) 28.sp else 32.sp,
                        lineHeight = if (largeText) 31.sp else 35.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
                    modifier = Modifier.fillMaxWidth().semantics { heading() })
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.support_prompt_body), style = body, color = SupportMuted)
                Spacer(Modifier.height(20.dp))
                SupportSocialButton(true, label) { open(DEVELOPER_INSTAGRAM_URL) }
                Spacer(Modifier.height(10.dp))
                SupportSocialButton(false, label) { open(DEVELOPER_GITHUB_URL) }
                failedUrl?.let { url ->
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(Res.string.support_prompt_link_failed), style = body,
                        color = SupportMuted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    SelectionContainer { Text(url, style = body, color = SupportCream) }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onDismiss, Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = SupportCream)) {
                    Text(stringResource(Res.string.support_prompt_later), style = label)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(optedOut, role = Role.Checkbox, onValueChange = onOptOut)
            .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(optedOut, onCheckedChange = null, colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF1ED760), uncheckedColor = SupportMuted, checkmarkColor = SupportSurface))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.support_prompt_hide), style = body, color = SupportMuted,
                modifier = Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SupportSocialButton(instagram: Boolean, label: TextStyle, onClick: () -> Unit) {
    val foreground = if (instagram) Color(0xFF07150B) else SupportCream
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape,
        border = if (instagram) null else BorderStroke(1.dp, SupportMuted),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (instagram) Color(0xFF1ED760) else Color.Transparent, contentColor = foreground),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Icon(painterResource(if (instagram) Res.drawable.support_instagram else Res.drawable.support_github), null, Modifier.size(24.dp))
        Text(if (instagram) "Instagram · @calestaan" else "GitHub · TanDjendra", style = label,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
        Icon(Icons.Rounded.ArrowOutward, null, Modifier.size(20.dp))
    }
}
