package com.example.notesapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.notesapp.data.NoteType
import kotlinx.coroutines.launch

/**
 * 分段滑块（备忘录 / 待办切换），扁平化设计。
 *
 * 实现要点：
 * - 不绘制轨道或选中项底色，仅通过文字颜色表示选中状态
 * - 拖动时文字颜色跟随手势变化，松手后吸附到对应选项
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

    // ⚠️ pointerInput(Unit) 只在首次组合时创建一次手势识别器，其闭包里捕获的
    // selected 始终是「首次组合时的值」。用户切换类型后 selected 已变化，但
    // onDragCancel 里仍然读旧值——取消拖拽会把滑块弹回错误的（旧的）那一侧。
    // 用 rememberUpdatedState 让手势闭包读取最新值，同时不改变 pointerInput 的 key
    // （key 保持 Unit，避免拖动途中重启识别器导致手势中断）。
    val currentSelected by rememberUpdatedState(selected)

    // 文字颜色随当前选中项及拖动进度变化
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .height(44.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                shape = CircleShape
            )
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
                        val target = if (currentSelected == NoteType.TODO) 1f else 0f
                        scope.launch {
                            animOffset.animateTo(target, springSpec)
                        }
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
