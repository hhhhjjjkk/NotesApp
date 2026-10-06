package com.example.notesapp.ui.theme

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 玻璃表面（Glass Surface）。
 *
 * ## 为什么保留 liquidGlassSurface 这个名字
 * 本函数经历过「拟物玻璃 → 全面扁平 → 回归玻璃」的演进，调用点有 6 处。
 * 为不改动它们，函数名保持不变，语义已从「仅裁剪」升级为「绘制真实的玻璃质感」。
 *
 * ## 视觉构成（一次绘制完成，无额外布局节点）
 * 1. **顶部高光**：自上而下的白色线性渐变，模拟环境光在玻璃上缘的反射。
 *    这是「像玻璃」最关键的一笔——没有它，半透明色块只会显得脏。
 * 2. **发丝描边**：沿形状内缘的一圈极细描边，勾勒玻璃的厚度边界。
 * 3. **半透明着色**（可选 [tint]）：叠一层主题色，让玻璃带上内容色倾向。
 *
 * ## 性能
 * 所有绘制都在**单个 [drawWithCache]** 中完成，且 Brush / Outline 都在缓存块里构建：
 * 只有尺寸或参数变化时才重建，每帧只是把已构建好的对象提交给 GPU。
 * 相比「叠多个 background / border 子节点」，这里零额外布局、零额外合成层。
 *
 * @param shape 裁剪与描边的形状（圆角矩形 / 胶囊）
 * @param isDark 决定高光与描边的明暗配方：深色下用白光提亮，浅色下用暗线勾勒
 * @param tint 可选的半透明着色层；默认完全透明（调用方通常已自行设过背景色）
 * @param borderWidth 发丝描边宽度；传 0.dp 可关闭描边
 * @param highlight 顶部高光强度 0f~1f；希望表面更「实」时可调低
 */
fun Modifier.liquidGlassSurface(
    shape: Shape,
    isDark: Boolean,
    tint: Color = Color.Transparent,
    borderWidth: Dp = 1.dp,
    highlight: Float = if (isDark) 0.16f else 0.45f
): Modifier = this
    .clip(shape)
    .drawWithCache {
        // 顶部高光：从形状上缘的亮色渐变到中段完全透明。
        // 只覆盖上半部分（endY = height * 0.55f），避免高光压到内容上显得发灰。
        val highlightBrush = if (highlight > 0f && size.height > 0f) {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = highlight),
                    Color.White.copy(alpha = 0f)
                ),
                startY = 0f,
                endY = size.height * 0.55f
            )
        } else {
            null
        }

        val borderColor = if (isDark) {
            Color.White.copy(alpha = 0.14f)
        } else {
            Color.Black.copy(alpha = 0.07f)
        }
        val strokeWidth = borderWidth.toPx()

        // 描边要画在形状**内部**：直接按原尺寸描边的话，外侧一半会被 clip 裁掉，
        // 视觉上只剩半条线。所以按描边宽度内缩后再取形状轮廓。
        // Outline 是密封类（Rectangle/Rounded/Generic），只有 Generic 直接携带 path，
        // 因此统一转换成 Path 描边，兼容全部形状且不依赖 drawOutline 扩展。
        val strokePath = if (strokeWidth > 0f) {
            val insetW = (size.width - strokeWidth).coerceAtLeast(0f)
            val insetH = (size.height - strokeWidth).coerceAtLeast(0f)
            if (insetW > 0f && insetH > 0f) {
                when (val outline = shape.createOutline(Size(insetW, insetH), layoutDirection, this)) {
                    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                    is Outline.Generic -> outline.path
                }
            } else {
                null
            }
        } else {
            null
        }
        val strokeInset = strokeWidth / 2f

        onDrawWithContent {
            // 绘制顺序（自下而上）：着色底 → 顶部高光 → 内容 → 描边。
            //
            // 为什么着色底要由本函数画，而不是像以前那样留给调用方的 containerColor：
            // Card / OutlinedTextField 这类组件会在 drawContent() 内部画自己的背景，
            // 若把高光画在它之前就会被那个背景盖住、完全看不见；
            // 画在它之后又会让高光盖在**文字**上，把标题洗淡。
            // 因此由本函数统一承担「底 + 高光」，调用方把 containerColor 设为透明，
            // 高光才能精确地落在底色与内容之间。
            //
            // 对于用 `Modifier.background(...)` 自带底色的调用方（FAB、底部工具条），
            // tint 传默认的透明即可：background 会先画，本函数的高光正好叠在其上、内容之下。
            if (tint.alpha > 0f) {
                drawRect(color = tint)
            }
            highlightBrush?.let { drawRect(brush = it) }
            drawContent()
            if (strokePath != null) {
                translate(strokeInset, strokeInset) {
                    drawPath(
                        path = strokePath,
                        color = borderColor,
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
        }
    }

/**
 * 真实高斯模糊，**仅在 API 31+ 生效**（低版本静默降级为无模糊）。
 *
 * 这是 GPU 逐帧模糊，成本不低：只适合小面积、静态或低频变化的元素。
 * 全屏背景请用 [AppBackground] 的「CPU 一次性模糊 + 放大」方案，
 * 那条路径在 API 26+ 全平台有效且每帧零开销。
 */
fun Modifier.softBlur(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0.dp) {
        this.blur(radius)
    } else {
        this
    }

/**
 * 按压反馈：轻微缩小，松开即恢复（无弹簧回弹，符合克制的交互语气）。
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
        label = "pressScale"
    )
    val modifier = Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    return modifier to interactionSource
}
