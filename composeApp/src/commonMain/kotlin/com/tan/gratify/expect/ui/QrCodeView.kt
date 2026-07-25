package com.tan.gratify.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
expect fun QrCodeView(
    content: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
)
