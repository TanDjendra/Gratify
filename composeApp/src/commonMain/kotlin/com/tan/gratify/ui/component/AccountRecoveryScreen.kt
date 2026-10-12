package com.tan.gratify.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.theme.GratifyColors
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.account_library_recovery_message
import gratify.composeapp.generated.resources.account_library_retry
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountRecoveryScreen(loading: Boolean, retry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(GratifyColors.Background), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (loading) CircularProgressIndicator()
            else {
                Text(stringResource(Res.string.account_library_recovery_message),
                    color = GratifyColors.TextPrimary, textAlign = TextAlign.Center)
                TextButton(onClick = retry) { Text(stringResource(Res.string.account_library_retry)) }
            }
        }
    }
}
