package dev.edgellm.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Chat : Screen("chat")
    data object Settings : Screen("settings")
}
