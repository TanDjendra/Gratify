package com.tan.gratify.ui.component

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.tan.gratify.viewModel.SharedViewModel

@Composable
actual fun DesktopWorkspace(
    navController: NavHostController,
    sharedViewModel: SharedViewModel,
    profileName: String?,
    profileImage: String?,
    chromeVisible: Boolean,
    content: @Composable () -> Unit,
) = content()
