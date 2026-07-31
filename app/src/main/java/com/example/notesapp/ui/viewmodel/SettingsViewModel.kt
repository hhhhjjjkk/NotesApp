package com.example.notesapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notesapp.data.DataStoreManager
import com.example.notesapp.data.ThemeColor
import com.example.notesapp.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val dataStoreManager: DataStoreManager) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = dataStoreManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val themeColor: StateFlow<ThemeColor> = dataStoreManager.themeColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeColor.ANDROID_BLUE)

    val cardRadius: StateFlow<Int> = dataStoreManager.cardRadius
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 12)

    val cardShadow: StateFlow<Boolean> = dataStoreManager.cardShadow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { dataStoreManager.setThemeMode(mode) }
    }

    fun setThemeColor(color: ThemeColor) {
        viewModelScope.launch { dataStoreManager.setThemeColor(color) }
    }

    fun setCardRadius(radius: Int) {
        viewModelScope.launch { dataStoreManager.setCardRadius(radius) }
    }

    fun setCardShadow(enabled: Boolean) {
        viewModelScope.launch { dataStoreManager.setCardShadow(enabled) }
    }
}
