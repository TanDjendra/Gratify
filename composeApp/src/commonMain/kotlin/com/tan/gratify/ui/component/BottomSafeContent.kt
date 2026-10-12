package com.tan.gratify.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tan.gratify.extension.copy

/** Reserve the measured bottom controls once, including screens without their own list padding. */
@Composable
fun BottomSafeContent(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
    Box(modifier.fillMaxSize().padding(bottom).consumeWindowInsets(bottom)) {
        content(padding.copy(bottom = 0.dp))
    }
}
