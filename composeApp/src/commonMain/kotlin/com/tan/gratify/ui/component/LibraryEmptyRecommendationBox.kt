package com.tan.gratify.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tan.gratify.extension.angledGradientBackground
import com.tan.gratify.ui.theme.typo
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.library_utility_label
import gratify.composeapp.generated.resources.library_recommendation_label

@Composable
fun LibraryEmptyRecommendationBox(
    modifier: Modifier = Modifier,
    iconRes: DrawableResource,
    gradientColors: List<Color>,
    emptyDescText: StringResource,
    emptyRecText: StringResource,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.02f)
                        )
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon header with gradient circle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .angledGradientBackground(gradientColors, 45f)
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Static subtitle or info badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(gradientColors.first().copy(alpha = 0.15f))
                        .border(
                            width = 0.5.dp,
                            color = gradientColors.first().copy(alpha = 0.3f),
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Info",
                        style = typo().bodySmall.copy(
                            fontSize = 11.sp,
                            color = gradientColors.first()
                        )
                    )
                }
            }

            // Features & Utility Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(Res.string.library_utility_label),
                    style = typo().titleSmall.copy(
                        color = Color.White.copy(alpha = 0.9f),
                        letterSpacing = 0.5.sp
                    )
                )
                Text(
                    text = stringResource(emptyDescText),
                    style = typo().bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.65f),
                        lineHeight = 20.sp
                    )
                )
            }

            // Divider
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.05f))
            )

            // Recommendation Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(Res.string.library_recommendation_label),
                    style = typo().titleSmall.copy(
                        color = gradientColors.first(),
                        letterSpacing = 0.5.sp
                    )
                )
                Text(
                    text = stringResource(emptyRecText),
                    style = typo().bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.65f),
                        lineHeight = 20.sp
                    )
                )
            }
        }
    }
}
