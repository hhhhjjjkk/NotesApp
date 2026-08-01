package com.example.notesapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.notesapp.data.ThemeColor
import com.example.notesapp.data.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = AndroidBlue,
    secondary = AndroidBlue,
    tertiary = AndroidBlue,
    background = DarkBackground,
    surface = DarkCardBackground,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = AndroidBlue,
    secondary = AndroidBlue,
    tertiary = AndroidBlue,
    background = Color(0xFFFDFDFD),
    surface = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

@Composable
fun NotesAppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themeColor: ThemeColor = ThemeColor.ANDROID_BLUE,
    backgroundUri: String? = null,
    backgroundDim: Float = 0.55f,
    darkTheme: Boolean = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    },
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val accent = themeColor.toComposeColor()
    val hasCustomBackground = !backgroundUri.isNullOrBlank()
    // 有自定义背景时，background 和 surface 都改为半透明遮罩色，让背景图透过 Scaffold 显示
    val resolvedBackground = resolveBackgroundColor(hasCustomBackground, darkTheme, backgroundDim)
    // surface 比 background 略不透明，保留层次感但避免色差过大
    val resolvedSurface = if (hasCustomBackground) {
        resolveBackgroundColor(hasCustomBackground, darkTheme, (backgroundDim + 0.08f).coerceAtMost(0.92f))
    } else {
        resolvedBackground
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            (if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
                .copy(background = resolvedBackground, surface = resolvedSurface)
        }

        darkTheme -> DarkColorScheme.copy(
            primary = accent,
            secondary = accent,
            tertiary = accent,
            background = resolvedBackground,
            surface = resolvedSurface
        )
        else -> LightColorScheme.copy(
            primary = accent,
            secondary = accent,
            tertiary = accent,
            background = resolvedBackground,
            surface = resolvedSurface
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

@Composable
fun NotesAppTheme(
    darkTheme: Boolean,
    themeColor: ThemeColor = ThemeColor.ANDROID_BLUE,
    backgroundUri: String? = null,
    backgroundDim: Float = 0.55f,
    content: @Composable () -> Unit
) {
    NotesAppTheme(
        themeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
        themeColor = themeColor,
        backgroundUri = backgroundUri,
        backgroundDim = backgroundDim,
        content = content
    )
}
