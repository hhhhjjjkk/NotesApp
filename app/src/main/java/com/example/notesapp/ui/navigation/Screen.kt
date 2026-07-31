package com.example.notesapp.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Editor : Screen("editor/{noteId}") {
        fun createRoute(noteId: Long = 0L) = "editor/$noteId"
    }
    data object Settings : Screen("settings")
}
