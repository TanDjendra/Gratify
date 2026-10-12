package com.tan.gratify.ui.component

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.tan.gratify.viewModel.SharedViewModel

/** Desktop chrome surrounds the same navigation graph used by Android. */
@Composable
expect fun DesktopWorkspace(
    navController: NavHostController,
    sharedViewModel: SharedViewModel,
    profileName: String?,
    profileImage: String?,
    chromeVisible: Boolean,
    content: @Composable () -> Unit,
)
