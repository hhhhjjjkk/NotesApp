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

// ===== 玻璃质感令牌 =====
// 玻璃表面 = 半透明底色 + 顶部高光 + 发丝描边（后两者由 liquidGlassSurface 绘制）。
// 这里的停产色只负责「底色」那一层，因此都用较高的 alpha（0.6~0.85）：
// 太低会让壁纸细节穿透到看不清文字，太高则失去玻璃的通透感。

/** 浅色模式玻璃底色：偏白，让卡片在浅背景上浮起来 */
val GlassLightSurface = Color(0xFFFFFFFF).copy(alpha = 0.72f)

/** 深色模式玻璃底色：偏深灰，保留层次但不至于纯黑 */
val GlassDarkSurface = Color(0xFF2A2A2E).copy(alpha = 0.68f)

/** 玻璃上的浮层（底部工具条、悬浮栏）：需要比普通卡片更实，避免其下内容干扰阅读 */
val GlassLightElevated = Color(0xFFFFFFFF).copy(alpha = 0.86f)
val GlassDarkElevated = Color(0xFF323236).copy(alpha = 0.82f)

/** 玻璃描边：浅色下用极淡的黑，深色下用极淡的白 */
val GlassLightBorder = Color(0x14000000)
val GlassDarkBorder = Color(0x1FFFFFFF)

/**
 * 把笔记卡片的柔和色转成**玻璃用底色**：在浅色下把颜色往白里提一点，
 * 既保留颜色辨识度（用户靠颜色区分笔记），又让高光有发挥空间。
 * 深色下压暗并压低饱和，避免彩色卡片在暗背景上过于刺眼。
 */
fun Color.toGlassSurface(isDark: Boolean): Color = if (isDark) {
    darken(0.55f).copy(alpha = 0.62f)
} else {
    this.copy(alpha = 0.78f)
}

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

/** 依据 RGB 亮度判断深浅（忽略 alpha），供玻璃/明暗分支判断复用。 */
fun Color.isDarkColor(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f
