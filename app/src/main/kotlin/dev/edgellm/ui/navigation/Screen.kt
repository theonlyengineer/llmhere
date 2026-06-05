package dev.edgellm.ui.navigation

sealed class Screen(val route: String) {
    data object Chat : Screen("chat")
    data object Settings : Screen("settings")
}
