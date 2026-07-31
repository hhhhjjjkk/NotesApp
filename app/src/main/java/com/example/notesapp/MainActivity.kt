package com.example.notesapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.notesapp.data.NoteDatabase
import com.example.notesapp.data.NoteRepository
import com.example.notesapp.data.ThemeMode
import com.example.notesapp.ui.navigation.NotesNavHost
import com.example.notesapp.ui.theme.NotesAppTheme
import com.example.notesapp.ui.viewmodel.NotesViewModel
import com.example.notesapp.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = NoteDatabase.getInstance(applicationContext)
        val repository = NoteRepository(database.noteDao())
        val dataStoreManager = (application as NotesApplication).dataStoreManager

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel {
                SettingsViewModel(dataStoreManager)
            }
            val notesViewModel: NotesViewModel = viewModel {
                NotesViewModel(repository)
            }

            val themeMode by settingsViewModel.themeMode.collectAsState()
            val themeColor by settingsViewModel.themeColor.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            NotesAppTheme(
                darkTheme = darkTheme,
                themeColor = themeColor
            ) {
                val navController = rememberNavController()
                NotesNavHost(
                    navController = navController,
                    notesViewModel = notesViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}
