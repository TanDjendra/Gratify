package com.tan.gratify.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.poppins_bold
import gratify.composeapp.generated.resources.poppins_medium
import gratify.composeapp.generated.resources.poppins_regular
import org.jetbrains.compose.resources.Font

@Composable
fun fontFamily(): FontFamily {
    val regular = Font(Res.font.poppins_regular, FontWeight.Normal)
    val medium = Font(Res.font.poppins_medium, FontWeight.Medium)
    val bold = Font(Res.font.poppins_bold, FontWeight.Bold)
    return remember(regular, medium, bold) { FontFamily(regular, medium, bold) }
}

/** Text styles inherit the content color, so selected controls remain readable. */
@Composable
fun typo(): Typography {
    val family = fontFamily()
    return remember(family) {
        fun style(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) =
            TextStyle(
                fontFamily = family,
                fontWeight = weight,
                fontSize = size.sp,
                lineHeight = lineHeight.sp,
                letterSpacing = tracking.sp,
            )
        Typography(
            displayLarge = style(48, 56, FontWeight.Bold, -1.5f),
            displayMedium = style(36, 44, FontWeight.Bold, -1f),
            displaySmall = style(30, 38, FontWeight.Bold, -0.7f),
            headlineLarge = style(28, 36, FontWeight.Bold, -0.6f),
            headlineMedium = style(24, 32, FontWeight.Bold, -0.4f),
            headlineSmall = style(20, 28, FontWeight.Bold, -0.3f),
            titleLarge = style(24, 32, FontWeight.Bold, -0.4f),
            titleMedium = style(17, 24, FontWeight.Bold, -0.2f),
            titleSmall = style(14, 20, FontWeight.Medium),
            bodyLarge = style(16, 24),
            bodyMedium = style(14, 21),
            bodySmall = style(12, 18),
            labelLarge = style(14, 20, FontWeight.Medium),
            labelMedium = style(13, 18, FontWeight.Medium),
            labelSmall = style(11, 16, FontWeight.Medium),
        )
    }
}
