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

    // 启动时主题信息直接影响首帧配色，使用 Eagerly 让 Flow 在 ViewModel 创建时
    // 立即开始收集 DataStore，避免首屏先渲染默认色再闪烁切换。
    val themeMode: StateFlow<ThemeMode> = dataStoreManager.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val themeColor: StateFlow<ThemeColor> = dataStoreManager.themeColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeColor.ANDROID_BLUE)

    val cardRadius: StateFlow<Int> = dataStoreManager.cardRadius
        .stateIn(viewModelScope, SharingStarted.Eagerly, 12)

    val cardShadow: StateFlow<Boolean> = dataStoreManager.cardShadow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val backgroundUri: StateFlow<String?> = dataStoreManager.backgroundUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    fun setBackgroundUri(uri: String?) {
        viewModelScope.launch { dataStoreManager.setBackgroundUri(uri) }
    }
}
