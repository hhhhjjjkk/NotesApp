package com.example.notesapp.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * 扁平化表面（Flattened Surface）。
 *
 * 应用整体已从「液态玻璃」改为扁平化设计：
 * - 不再叠加顶部高光渐变、边缘亮线、噪点与真实模糊
 * - 卡片 / 容器的观感只由纯色背景 + 圆角构成，层次交给色板（surface / surfaceVariant）
 * - 按压反馈保留轻微缩放，但去掉了"液态挤压回弹"的弹簧感
 *
 * 函数名沿用 [liquidGlassSurface]，是为了避免大面积改动调用点；
 * 语义已变为「应用统一的扁平表面处理（目前只有裁剪到形状）」。
 */

/**
 * 统一的扁平表面处理：只裁剪到目标形状。
 * 高光、描边、模糊均已移除；背景色由调用方的 background / containerColor 提供。
 */
fun Modifier.liquidGlassSurface(
    shape: Shape,
    @Suppress("UNUSED_PARAMETER") isDark: Boolean,
    @Suppress("UNUSED_PARAMETER") blurRadius: Dp = Dp.Unspecified,
    @Suppress("UNUSED_PARAMETER") borderWidth: Dp = Dp.Unspecified
): Modifier = this.clip(shape)

/**
 * 按压反馈：轻微缩小，松开即恢复（无弹簧回弹，符合扁平化的克制交互）。
 * 返回 (modifier, interactionSource)，interactionSource 需传入可点击组件。
 */
@Composable
fun rememberPressableGlassScale(
    pressedScale: Float = 0.97f
): Pair<Modifier, MutableInteractionSource> {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "flatPressScale"
    )
    val modifier = Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    return modifier to interactionSource
}
