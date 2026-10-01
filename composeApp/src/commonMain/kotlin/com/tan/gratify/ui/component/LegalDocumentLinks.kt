package com.tan.gratify.ui.component

import androidx.compose.foundation.layout.Column
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
import gratify.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalResourceApi::class)
@Composable
fun LegalDocumentLinks() {
    var document by rememberSaveable { mutableStateOf<String?>(null) }
    var content by rememberSaveable { mutableStateOf("") }
    var contactUnavailable by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Column {
        TextButton(onClick = { content = ""; document = "terms-of-use.md" }) { Text("Baca Terms of Use (draf)") }
        TextButton(onClick = { content = ""; document = "privacy-policy.md" }) { Text("Baca Privacy Policy (draf)") }
        TextButton(onClick = { contactUnavailable = runCatching { uriHandler.openUri("mailto:supportgratify@gmail.com") }.isFailure }) { Text("supportgratify@gmail.com") }
        if (contactUnavailable) SelectionContainer { Text("Aplikasi email belum tersedia. Anda dapat menyalin alamat supportgratify@gmail.com.") }
    }
    val file = document
    if (file != null) {
        LaunchedEffect(file) {
            content = try { Res.readBytes("files/legal/$file").decodeToString() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { "Dokumen belum dapat dibuka. Silakan coba lagi atau hubungi supportgratify@gmail.com." }
        }
        AlertDialog(
            onDismissRequest = { document = null },
            title = { Text(if (file == "terms-of-use.md") "Terms of Use — draf" else "Privacy Policy — draf") },
            text = { SelectionContainer { Text(content.ifBlank { "Memuat dokumen…" }, modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) } },
            confirmButton = { TextButton(onClick = { document = null }) { Text("Tutup") } },
        )
    }
}
