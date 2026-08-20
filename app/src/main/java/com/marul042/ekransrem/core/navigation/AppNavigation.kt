package com.marul042.ekransrem.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.Modifier

@Composable
fun AppNavigation(
    dashboard: @Composable (Modifier) -> Unit,
    settings: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AppDestination.Dashboard.route,
        modifier = modifier
    ) {
        composable(AppDestination.Dashboard.route) { dashboard(Modifier) }
        composable(AppDestination.Settings.route) { settings(Modifier) }
    }
}
