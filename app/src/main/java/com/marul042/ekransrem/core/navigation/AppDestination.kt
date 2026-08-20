package com.marul042.ekransrem.core.navigation

sealed class AppDestination(val route: String) {
    data object Dashboard : AppDestination("dashboard")
    data object Settings : AppDestination("settings")
}
