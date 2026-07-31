package com.example.notesapp.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.notesapp.data.ThemeColor

// Morandi / pastel note card colors (light variants)
val NoteYellow = Color(0xFFFFF9C4)
val NoteGreen = Color(0xFFE8F5E9)
val NoteBlue = Color(0xFFE3F2FD)
val NotePurple = Color(0xFFF3E5F5)
val NotePink = Color(0xFFFCE4EC)
val NoteOrange = Color(0xFFFFF3E0)
val NoteTeal = Color(0xFFE0F2F1)
val NoteGray = Color(0xFFF5F5F5)

// Dark surface for cards in dark mode
val DarkCardBackground = Color(0xFF2D2D2D)
val DarkBackground = Color(0xFF1E1E1E)

// Theme accent colors
val AndroidBlue = Color(0xFF4285F4)
val EmeraldGreen = Color(0xFF34A853)
val Violet = Color(0xFF9C27B0)
val CoralOrange = Color(0xFFFF7043)
val RosePink = Color(0xFFF06292)
val MorandiTeal = Color(0xFF4DB6AC)

val noteCardColors = listOf(
    NoteYellow,
    NoteGreen,
    NoteBlue,
    NotePurple,
    NotePink,
    NoteOrange,
    NoteTeal,
    NoteGray
)

fun ThemeColor.toComposeColor(): Color = when (this) {
    ThemeColor.ANDROID_BLUE -> AndroidBlue
    ThemeColor.EMERALD_GREEN -> EmeraldGreen
    ThemeColor.VIOLET -> Violet
    ThemeColor.CORAL_ORANGE -> CoralOrange
    ThemeColor.ROSE_PINK -> RosePink
    ThemeColor.MORANDI_TEAL -> MorandiTeal
}

fun Int.toNoteColor(isDark: Boolean): Color {
    if (this == 0) return if (isDark) DarkCardBackground else NoteYellow
    val base = Color(this)
    return if (isDark) base.darken(0.35f) else base
}

fun Color.darken(factor: Float = 0.3f): Color {
    return Color(
        red = (red * (1 - factor)).coerceIn(0f, 1f),
        green = (green * (1 - factor)).coerceIn(0f, 1f),
        blue = (blue * (1 - factor)).coerceIn(0f, 1f),
        alpha = alpha
    )
}
