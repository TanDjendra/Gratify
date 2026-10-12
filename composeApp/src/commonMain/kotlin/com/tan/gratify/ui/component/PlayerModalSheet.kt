package com.tan.gratify.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.tan.gratify.ui.theme.GratifyColors
import kotlinx.coroutines.launch

/** Keep the player mounted until its sheet is hidden, including repeated close/back requests. */
internal class PlayerSheetDismissal(
    private val hide: suspend () -> Unit,
    private val isHidden: () -> Boolean,
    private val onDismiss: () -> Unit,
) {
    private var dismissing = false
    private var completed = false

    suspend fun dismiss() {
        if (dismissing || completed) return
        dismissing = true
        try {
            hide()
            complete()
        } finally {
            // An interrupted gesture/animation must leave the panel usable and closable.
            dismissing = false
        }
    }

    fun complete() {
        if (!completed && isHidden()) {
            completed = true
            onDismiss()
        }
    }
}

/** Both full-screen player panels use the app's Material sheet motion and fading scrim. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerModalSheet(
    onDismiss: () -> Unit,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val currentOnDismiss = rememberUpdatedState(onDismiss)
    val dismissal = remember(sheet) {
        PlayerSheetDismissal(
            hide = { sheet.hide() },
            isHidden = { !sheet.isVisible },
            onDismiss = { currentOnDismiss.value() },
        )
    }
    val dismiss = remember(dismissal, scope) { { scope.launch { dismissal.dismiss() }; Unit } }
    ModalBottomSheet(
        modifier = Modifier.fillMaxHeight(),
        // Material invokes this after Back, outside taps or a swipe have hidden the sheet.
        onDismissRequest = dismissal::complete,
        sheetState = sheet,
        containerColor = GratifyColors.Background,
        scrimColor = BottomSheetDefaults.ScrimColor,
        shape = RectangleShape,
        dragHandle = {},
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        content(dismiss)
    }
}
