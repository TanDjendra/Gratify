package com.tan.gratify.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tan.gratify.ui.theme.GratifyColors
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalResourceApi::class, ExperimentalLayoutApi::class)
@Composable
fun LegalDocumentLinks(showContact: Boolean = true, compact: Boolean = false, showTerms: Boolean = true, subtle: Boolean = false) {
    var document by rememberSaveable { mutableStateOf<String?>(null) }
    var content by rememberSaveable { mutableStateOf("") }
    var contactUnavailable by rememberSaveable { mutableStateOf(false) }
    var webUnavailable by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    @Composable fun LinkText(label: String) {
        if (subtle) Text(label, fontSize = 12.sp, color = GratifyColors.TextSecondary)
        else Text(label)
    }
    @Composable fun Links() {
        if (showTerms) TextButton(onClick = { content = ""; document = "terms-of-use.md" }) { LinkText(stringResource(Res.string.about_terms_label)) }
        TextButton(onClick = { content = ""; document = "privacy-policy.md" }) { LinkText(stringResource(Res.string.about_privacy_label)) }
        if (showContact) TextButton(onClick = { contactUnavailable = runCatching { uriHandler.openUri("mailto:supportgratify@gmail.com") }.isFailure }) { Text("supportgratify@gmail.com") }
        if (contactUnavailable) SelectionContainer { Text("Aplikasi email belum tersedia. Anda dapat menyalin alamat supportgratify@gmail.com.") }
    }
    if (compact) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Links() }
    else Column { Links() }
    val file = document
    if (file != null) {
        val webUrl = if (file == "terms-of-use.md") "https://gratify.web.id/terms/" else "https://gratify.web.id/privacy/"
        LaunchedEffect(file) {
            content = try { Res.readBytes("files/legal/$file").decodeToString() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { "Dokumen belum dapat dibuka. Silakan coba lagi atau hubungi supportgratify@gmail.com." }
        }
        AlertDialog(
            onDismissRequest = { document = null },
            title = { Text(if (file == "terms-of-use.md") "Terms of Use — draf" else "Privacy Policy") },
            text = { SelectionContainer { Text(content.ifBlank { "Memuat dokumen…" }, modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) } },
            confirmButton = {
                Column {
                    if (webUnavailable) SelectionContainer { Text("Browser belum tersedia. Tautan: $webUrl") }
                    Row {
                        TextButton(onClick = { webUnavailable = runCatching { uriHandler.openUri(webUrl) }.isFailure }) { Text("Buka di browser") }
                        TextButton(onClick = { document = null; webUnavailable = false }) { Text("Tutup") }
                    }
                }
            },
        )
    }
}
