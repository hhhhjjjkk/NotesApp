package com.example.notesapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.notesapp.data.NoteType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 液态玻璃分段滑块。
 *
 * 玻璃质感实现要点（避免"塑料感"）：
 * - 轨道（track）做凹陷：顶部内阴影 + 底部内高光，形成下凹的容纳槽
 * - 滑块（thumb）做凸起：顶部窄高光（镜面反射）+ 底部柔阴影 + 边缘亮线 + 软投影
 * - 全部用 drawBehind 绘制在内容之下，半透明叠加让背景透出，而非不透明色块
 * - 拖动跟手 snapTo，松手 spring 物理回弹
 *
 * @param selected 当前选中类型，[NoteType.NOTE] 或 [NoteType.TODO]
 * @param onSelected 类型切换回调
 */
@Composable
fun LiquidSegmentedSlider(
    selected: Int,
    onSelected: (Int) -> Unit,
    leftLabel: String,
    rightLabel: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val padDp = 4.dp
    val padPx = with(density) { padDp.toPx() }

    // 归一化偏移 0f..1f：0=左(备忘录)，1=右(代办)
    val animOffset = remember { Animatable(if (selected == NoteType.TODO) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }

    // 容器实测像素宽度，供拖动手势计算最大偏移
    var widthPx by remember { mutableStateOf(0f) }

    // 外部切换 selected 时同步动画（非拖动状态）
    LaunchedEffect(selected) {
        if (!dragging) {
            val target = if (selected == NoteType.TODO) 1f else 0f
            animOffset.animateTo(target, springSpec)
        }
    }

    // 主题色：用于实时跟随滑块的染色高光带
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(
        modifier = modifier
            .height(44.dp)
            .drawBehind {
                // 实时跟随滑块的主题色染色带，无底色、无渐变、无描边，彻底去框
                val h = size.height
                val thumbW = (size.width - 2 * padPx) / 2f
                if (thumbW > 0f) {
                    val bx = padPx + animOffset.value * thumbW
                    drawRect(
                        color = primary.copy(alpha = 0.12f),
                        topLeft = Offset(bx, 0f),
                        size = Size(thumbW, h)
                    )
                }
            }
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                    },
                    onDragEnd = {
                        dragging = false
                        val target = if (animOffset.value > 0.5f) 1f else 0f
                        scope.launch {
                            animOffset.animateTo(target, springSpec)
                            onSelected(if (target > 0.5f) NoteType.TODO else NoteType.NOTE)
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        val target = if (selected == NoteType.TODO) 1f else 0f
                        scope.launch { animOffset.animateTo(target, springSpec) }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val maxOffset = (widthPx - 2 * padPx) / 2f
                        if (maxOffset <= 0f) return@detectHorizontalDragGestures
                        val next = (animOffset.value + dragAmount / maxOffset)
                            .coerceIn(0f, 1f)
                        scope.launch { animOffset.snapTo(next) }
                    }
                )
            }
    ) {
        val thumbWidthDp = (maxWidth - padDp * 2) / 2
        val thumbWidthPx = with(density) { thumbWidthDp.toPx() }
        val thumbOffsetPx = animOffset.value * thumbWidthPx

        // 滑块覆盖区域的主题色染色，与轨道色带同色，强化"覆盖即变色"
        val thumbTint = primary.copy(alpha = if (isDark) 0.10f else 0.08f)

        // 滑块：保留主题色染色随手指实时位移；去掉所有玻璃渐变、投影、clip，
        // 仅保留基础背景色与主题色染色，彻底消除视觉"框"
        Box(
            modifier = Modifier
                .padding(vertical = padDp)
                .offset { IntOffset((padPx + thumbOffsetPx).roundToInt(), 0) }
                .width(thumbWidthDp)
                .fillMaxHeight()
                .drawBehind {
                    drawRect(thumbTint)
                }
        )

        // 左右文字：颜色随滑块实时插值（lerp），不再等 selected 切换后才变色——彻底消除"颜色延迟跟随"
        // animOffset: 0=左(备忘录)选中, 1=右(代办)选中
        val leftColor = lerp(primary, onSurfaceVariant, animOffset.value)
        val rightColor = lerp(onSurfaceVariant, primary, animOffset.value)
        Row(
            modifier = Modifier.fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = leftLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (animOffset.value < 0.5f) FontWeight.Bold else FontWeight.Medium,
                color = leftColor,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (selected != NoteType.NOTE) {
                            scope.launch {
                                animOffset.animateTo(0f, springSpec)
                                onSelected(NoteType.NOTE)
                            }
                        }
                    }
                    .padding(horizontal = 8.dp),
                textAlign = TextAlign.Center
            )
            Text(
                text = rightLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (animOffset.value >= 0.5f) FontWeight.Bold else FontWeight.Medium,
                color = rightColor,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (selected != NoteType.TODO) {
                            scope.launch {
                                animOffset.animateTo(1f, springSpec)
                                onSelected(NoteType.TODO)
                            }
                        }
                    }
                    .padding(horizontal = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

private val springSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium
)
