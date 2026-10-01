package com.tan.gratify.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.theme.GratifySpacing
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_COMMUTE
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_ENERGIZE
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_FEEL_GOOD
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_FOCUS
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_PARTY
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_RELAX
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_ROMANCE
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_SAD
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_SLEEP
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_WORKOUT
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private val homeFilters = listOf(
    Res.string.all to null, Res.string.relax to HOME_PARAMS_RELAX, Res.string.sleep to HOME_PARAMS_SLEEP,
    Res.string.energize to HOME_PARAMS_ENERGIZE, Res.string.sad to HOME_PARAMS_SAD,
    Res.string.romance to HOME_PARAMS_ROMANCE, Res.string.feel_good to HOME_PARAMS_FEEL_GOOD,
    Res.string.workout to HOME_PARAMS_WORKOUT, Res.string.party to HOME_PARAMS_PARTY,
    Res.string.commute to HOME_PARAMS_COMMUTE, Res.string.focus to HOME_PARAMS_FOCUS,
)

@Composable
fun HomeMoodFilters(selectedParams: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState())
            .padding(horizontal = GratifySpacing.Page, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        homeFilters.forEach { (label, params) ->
            Chip(text = stringResource(label), isSelected = selectedParams == params, onClick = { onSelect(params) })
        }
    }
}
