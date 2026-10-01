package com.tan.gratify.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.theme.GratifyColors
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.ui_back
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Readable controls over artwork without capturing or blurring the background. */
@Composable
fun ArtworkIconButton(
    resId: DrawableResource,
    modifier: Modifier = Modifier.size(48.dp),
    shape: Shape = CircleShape,
    tint: Color = GratifyColors.TextPrimary,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.clip(shape),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = GratifyColors.Navigation.copy(alpha = 0.9f),
            contentColor = tint,
        ),
    ) {
        Icon(painterResource(resId), contentDescription = stringResource(Res.string.ui_back), modifier = Modifier.size(22.dp))
    }
}
