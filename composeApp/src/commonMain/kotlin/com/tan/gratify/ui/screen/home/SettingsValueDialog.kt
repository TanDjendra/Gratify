package com.tan.gratify.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tan.gratify.viewModel.SettingAlertState
import com.tan.gratify.ui.theme.*

@Composable
internal fun SettingsValueDialog(state: SettingAlertState, onChange: (SettingAlertState) -> Unit, onDismiss: () -> Unit) {
    val input = state.textField
    val verification = input?.verifyCodeBlock?.invoke(input.value) ?: (true to null)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(state.title, style = typo().titleMedium) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.message?.takeIf(String::isNotBlank)?.let { Text(it, style = typo().bodyMedium) }
                if (input != null) OutlinedTextField(input.value,
                    { onChange(state.copy(textField = input.copy(value = it))) }, Modifier.fillMaxWidth(),
                    label = { Text(input.label) }, isError = !verification.first,
                    visualTransformation = if (input.sensitive) PasswordVisualTransformation() else VisualTransformation.None,
                    supportingText = { if (!verification.first) Text(verification.second.orEmpty()) },
                    trailingIcon = { if (!verification.first) Icon(Icons.Rounded.ErrorOutline, null) })
                state.selectOne?.let { selection -> selection.listSelect.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth().selectable(item.first, role = Role.RadioButton) {
                        onChange(state.copy(selectOne = selection.copy(listSelect = selection.listSelect.mapIndexed { i, option -> (i == index) to option.second })))
                    }.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(item.first, onClick = null); Spacer(Modifier.width(12.dp))
                        Text(item.second, Modifier.weight(1f), style = typo().bodyMedium)
                    }
                } }
                state.multipleSelect?.let { selection -> selection.listSelect.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth().toggleable(item.first, role = Role.Checkbox) { selected ->
                        onChange(state.copy(multipleSelect = selection.copy(listSelect = selection.listSelect.mapIndexed { i, option ->
                            (if (i == index) selected else option.first) to option.second
                        })))
                    }.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.first, onCheckedChange = null); Spacer(Modifier.width(12.dp))
                        Text(item.second, Modifier.weight(1f), style = typo().bodyMedium)
                    }
                } }
            }
        }, confirmButton = { TextButton(onClick = { onDismiss(); state.confirm.second(state) }, enabled = verification.first) { Text(state.confirm.first) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(state.dismiss) } })
}
