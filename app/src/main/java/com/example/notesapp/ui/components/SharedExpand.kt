package com.example.notesapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * 编辑页过渡（v3.12.0）：**从被点击卡片的 y 位置向上浮起 + 淡入**，
 * 关闭时向下沉回卡片位置并完全淡出。
 *
 * 关键设计：
 * 1. 起点位移用 [Rect.top]（卡片在屏幕中的比例位置），而非固定的「屏幕下方 18%」——
 *    这样无论你点的是列表顶部还是底部的卡片，页面都从那**张卡片的位置**浮起来。
 * 2. 关闭时 alpha 必须收到 **0**。此前 alpha 曲线为 0.7 + 0.3p，p=0 时仍有 0.7 不透明度，
 *    动画结束时页面停在卡片位置「半透明」，随后瞬间 pop 消失 —— 这就是用户看到的
 *    「退出时动画有残留」。
 *
 * 曲线：emphasized decelerate cubic-bezier(0.2, 0, 0, 1)，非线性先快后慢，380ms。
 */
private val SlideEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val SlideDuration = 380

@Composable
fun SharedExpandBox(
    from: Rect?,
    closing: Boolean,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
    /** 过渡进度 0..1 */
    content: @Composable androidx.compose.foundation.layout.BoxScope.(progress: Float) -> Unit
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(from, size) {
        if (from == null || size.width == 0 || size.height == 0) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.animateTo(1f, tween(SlideDuration, easing = SlideEasing))
    }

    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        progress.animateTo(0f, tween(SlideDuration, easing = SlideEasing))
        onClosed()
    }

    val p = progress.value
    val animate = from != null

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .onSizeChanged { size = it }
            .graphicsLayer {
                if (animate && size.height > 0 && from != null) {
                    // 起点 = 卡片在屏幕中的 y 位置：卡片靠下则浮起距离大，
                    // 靠上则浮起距离小，视觉上始终「从那张卡片浮上来」。
                    val startOffset = from.top * size.height
                    translationY = (1f - p) * startOffset
                    // ⚠️ 关闭时必须收到 0，否则残留半透明页面（v3.12.0 修复）
                    alpha = if (closing) p else (0.75f + 0.25f * p)
                } else {
                    translationY = 0f
                    alpha = 1f
                }
            }
    ) {
        content(p)
    }
}
