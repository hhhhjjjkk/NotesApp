package com.example.notesapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.notesapp.data.NoteType
import com.example.notesapp.ui.theme.glassBorder
import com.example.notesapp.ui.theme.glassHighlight
import com.example.notesapp.ui.theme.glassOverlayColor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 液态玻璃分段滑块。
 *
 * 设计要点：
 * - 胶囊形容器 + 内部可滑动的液态玻璃"滑块"，左侧标签"备忘录"、右侧"代办"
 * - 滑块跟手实时渲染：拖动时直接 snapTo 偏移，松手后 spring 物理回弹归位
 * - 滑块本身为半透明玻璃材质（径向高光 + 顶部高光渐变 + 边缘描边 + 阴影）
 * - 点击左右文字亦可切换，提升可用性
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

    BoxWithConstraints(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(glassOverlayColor(isDark).copy(alpha = if (isDark) 0.10f else 0.22f))
            .glassHighlight(CircleShape, isDark)
            .glassBorder(CircleShape, isDark, 1.dp)
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

        // 滑块：液态玻璃材质，随手指实时位移
        Box(
            modifier = Modifier
                .padding(vertical = padDp)
                .offset { IntOffset((padPx + thumbOffsetPx).roundToInt(), 0) }
                .width(thumbWidthDp)
                .fillMaxHeight()
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.15f),
                    spotColor = Color.Black.copy(alpha = 0.25f)
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.22f else 0.65f),
                            Color.White.copy(alpha = if (isDark) 0.10f else 0.40f)
                        )
                    )
                )
                .glassHighlight(CircleShape, isDark)
                .glassBorder(CircleShape, isDark, 1.dp)
        )

        // 左右文字：选中态加粗高亮
        Row(
            modifier = Modifier.fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = leftLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected == NoteType.NOTE) FontWeight.Bold else FontWeight.Medium,
                color = if (selected == NoteType.NOTE) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
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
                fontWeight = if (selected == NoteType.TODO) FontWeight.Bold else FontWeight.Medium,
                color = if (selected == NoteType.TODO) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
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
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)
