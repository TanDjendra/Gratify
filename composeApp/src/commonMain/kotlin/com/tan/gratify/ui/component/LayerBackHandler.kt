package com.tan.gratify.ui.component

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.backhandler.BackHandler

/** Let the system dismiss the keyboard before dismissing the current in-page layer. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun LayerBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled && WindowInsets.ime.getBottom(LocalDensity.current) == 0, onBack = onBack)
}
