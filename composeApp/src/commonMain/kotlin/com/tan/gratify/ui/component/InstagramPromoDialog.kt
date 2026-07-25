package com.tan.gratify.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import com.tan.gratify.ui.theme.typo
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.mono

@Composable
@ExperimentalMaterial3Api
fun InstagramPromoDialog(
    onDismissRequest: () -> Unit,
    onVisitInstagram: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        onDismissRequest = {
            onDismissRequest.invoke()
        },
        confirmButton = {
            TextButton(onClick = {
                onVisitInstagram.invoke()
                uriHandler.openUri("https://instagram.com/calestaan")
            }) {
                Text(
                    "Kunjungi",
                    style = typo().bodySmall,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismissRequest.invoke()
            }) {
                Text(
                    "Nanti",
                    style = typo().bodySmall,
                )
            }
        },
        icon = {
            Icon(painterResource(Res.drawable.mono), "App Icon", tint = Color.Unspecified)
        },
        title = {
            Text(
                "Temukan Kami di Instagram!",
                style = typo().labelSmall,
            )
        },
        text = {
            Text(
                "Dukung terus pengembangan Gratify dengan mengikuti akun Instagram @calestaan. Dapatkan info terbaru dan berinteraksi langsung!",
                textAlign = TextAlign.Center,
                style = typo().bodySmall,
            )
        },
    )
}
