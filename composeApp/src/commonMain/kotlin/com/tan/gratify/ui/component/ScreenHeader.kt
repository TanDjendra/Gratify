package com.tan.gratify.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.ui_open_profile
import org.jetbrains.compose.resources.stringResource

/** Consistent title, avatar, spacing and colors across the main destinations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenHeader(
    title: String,
    profileName: String?,
    profileImage: String?,
    onOpenProfile: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val profileDescription = stringResource(Res.string.ui_open_profile)
    TopAppBar(
        navigationIcon = {
            Box(Modifier.padding(start = 12.dp, end = 8.dp).size(48.dp)
                .semantics { contentDescription = profileDescription }
                .clickable(onClick = onOpenProfile), contentAlignment = androidx.compose.ui.Alignment.Center) {
                UserAvatar(imageUrl = profileImage, name = profileName, modifier = Modifier.size(34.dp))
            }
        },
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1) },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
    )
}
