package com.example.notesapp.ui.theme

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃（Liquid Glass）质感工具集。
 *
 * 设计要点：
 * - 半透明叠加 + 顶部高光渐变，模拟玻璃材质对光的折射
 * - 边缘亮线（描边）模拟玻璃边沿的高光
 * - 按压时轻微缩放 + 形变，模拟液态的"挤压回弹"
 * - Android 12+ 对容器自身内容做轻微模糊，增强磨砂质感
 *
 * 说明：Compose 的 Modifier.blur 只能模糊"自身渲染内容"，无法模糊身后其他
 * Composable。因此本实现通过材质叠加营造玻璃观感，在所有版本可用；API 31+
 * 额外叠加真实模糊。
 */

// 玻璃容器基础叠加色（半透明白，依明暗模式）
fun glassOverlayColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.35f)

// 玻璃边缘高光色
private fun glassEdgeColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.7f)

/**
 * 顶部高光渐变：从亮到透明，模拟玻璃上沿受光。
 * 先 clip 到形状，确保高光遵循圆角边界。
 */
fun Modifier.glassHighlight(
    shape: Shape,
    isDark: Boolean
): Modifier = this
    .clip(shape)
    .drawWithContent {
        drawContent()
        val top = glassEdgeColor(isDark)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(top, top.copy(alpha = 0f)),
                startY = 0f,
                endY = size.height * 0.5f
            )
        )
    }

/**
 * 玻璃边缘描边：沿形状绘制一圈细高光，模拟玻璃边沿。
 */
fun Modifier.glassBorder(
    shape: Shape,
    isDark: Boolean,
    width: Dp = 1.dp
): Modifier = this.border(
    width = width,
    color = glassEdgeColor(isDark),
    shape = shape
)

/**
 * 真实背景模糊：仅 Android 12+ 生效，对容器自身内容做轻微高斯模糊，
 * 营造磨砂玻璃质感。低版本自动降级为无模糊。
 */
fun Modifier.glassBlur(radius: Dp = 8.dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) this.blur(radius) else this

/**
 * 液态按压形变：按下时整体轻微缩小，松开后 spring 回弹（液态感）。
 * 返回 (modifier, interactionSource)，interactionSource 需传入可点击组件。
 */
@Composable
fun rememberPressableGlassScale(
    pressedScale: Float = 0.96f
): Pair<Modifier, MutableInteractionSource> {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "glassPressScale"
    )
    val modifier = Modifier.graphicsLayerScale(scale)
    return modifier to interactionSource
}

private fun Modifier.graphicsLayerScale(scale: Float): Modifier =
    this.then(
        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )

/**
 * 完整液态玻璃表面：组合高光渐变 + 边缘描边 + （可选）模糊。
 * 适用于卡片、容器等。
 */
fun Modifier.liquidGlassSurface(
    shape: Shape,
    isDark: Boolean,
    blurRadius: Dp = 0.dp,
    borderWidth: Dp = 1.dp
): Modifier = this
    .let { if (blurRadius > 0.dp) it.glassBlur(blurRadius) else it }
    .glassHighlight(shape, isDark)
    .glassBorder(shape, isDark, borderWidth)
