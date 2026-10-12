package com.tan.gratify.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** Keep the same sheet through loading/results while consulting the latest dismissal guard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberBusyModalSheetState(busy: Boolean, skipPartiallyExpanded: Boolean = true): SheetState {
    val latestBusy = rememberUpdatedState(busy)
    // Material3 uses this callback as a rememberSaveable input. Capturing busy directly
    // changes its identity and resets the sheet to Hidden on every busy transition.
    val confirm = remember { { value: SheetValue -> !latestBusy.value || value != SheetValue.Hidden } }
    return rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded, confirmValueChange = confirm)
}
