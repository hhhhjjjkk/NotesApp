package com.example.notesapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "notes_settings")

class DataStoreManager(private val context: Context) {

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_COLOR = stringPreferencesKey("theme_color")
        val CARD_RADIUS = intPreferencesKey("card_radius")
        val CARD_SHADOW = booleanPreferencesKey("card_shadow")
        val BACKGROUND_URI = stringPreferencesKey("background_uri")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.fromString(prefs[THEME_MODE] ?: ThemeMode.SYSTEM.name)
    }

    val themeColor: Flow<ThemeColor> = context.dataStore.data.map { prefs ->
        ThemeColor.fromString(prefs[THEME_COLOR] ?: ThemeColor.ANDROID_BLUE.name)
    }

    val cardRadius: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[CARD_RADIUS] ?: 12
    }

    val cardShadow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CARD_SHADOW] ?: true
    }

    // 自定义背景图 URI（字符串形式，null 表示使用默认纯色背景）
    val backgroundUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[BACKGROUND_URI]
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setThemeColor(color: ThemeColor) {
        context.dataStore.edit { it[THEME_COLOR] = color.name }
    }

    suspend fun setCardRadius(radius: Int) {
        context.dataStore.edit { it[CARD_RADIUS] = radius }
    }

    suspend fun setCardShadow(enabled: Boolean) {
        context.dataStore.edit { it[CARD_SHADOW] = enabled }
    }

    suspend fun setBackgroundUri(uri: String?) {
        context.dataStore.edit {
            if (uri == null) it.remove(BACKGROUND_URI) else it[BACKGROUND_URI] = uri
        }
    }
}

enum class ThemeMode {
    LIGHT, DARK, SYSTEM;

    companion object {
        fun fromString(value: String): ThemeMode = entries.find { it.name == value } ?: SYSTEM
    }
}

enum class ThemeColor {
    ANDROID_BLUE, EMERALD_GREEN, VIOLET, CORAL_ORANGE, ROSE_PINK, MORANDI_TEAL;

    companion object {
        fun fromString(value: String): ThemeColor = entries.find { it.name == value } ?: ANDROID_BLUE
    }
}
