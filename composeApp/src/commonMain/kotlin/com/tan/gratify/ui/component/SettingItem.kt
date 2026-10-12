package com.tan.gratify.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.theme.*

@Composable
fun SettingItem(title: String = "", subtitle: String = "", smallSubtitle: Boolean = false,
    isEnable: Boolean = true, onClick: (() -> Unit)? = null,
    switch: Pair<Boolean, ((Boolean) -> Unit)>? = null,
    otherView: @Composable (() -> Unit)? = null) {
    val interaction = when {
        switch != null -> Modifier.toggleable(value = switch.first, enabled = isEnable, role = Role.Switch, onValueChange = switch.second)
        onClick != null -> Modifier.clickable(enabled = isEnable, role = Role.Button, onClick = onClick)
        else -> Modifier
    }
    Row(Modifier.fillMaxWidth().then(interaction).then(if (!isEnable) Modifier.semantics { disabled() } else Modifier)
        .heightIn(min = 64.dp).padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = typo().titleSmall, color = if (isEnable) GratifyColors.TextPrimary else GratifyColors.TextSecondary)
            if (subtitle.isNotBlank()) Text(subtitle, style = if (smallSubtitle) typo().bodySmall else typo().bodyMedium,
                color = GratifyColors.TextSecondary)
            otherView?.let { Spacer(Modifier.height(8.dp)); it() }
        }
        if (switch != null) Switch(checked = switch.first, onCheckedChange = null, enabled = isEnable)
    }
}
