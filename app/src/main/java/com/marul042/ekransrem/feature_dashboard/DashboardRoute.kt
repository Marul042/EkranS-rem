package com.marul042.ekransrem.feature_dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.marul042.ekransrem.ui.UsageUiState

@Composable
fun DashboardRoute(
    state: UsageUiState,
    modifier: Modifier = Modifier,
    content: @Composable (UsageUiState, Modifier) -> Unit
) {
    content(state, modifier)
}
