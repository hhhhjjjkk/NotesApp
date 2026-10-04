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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
 * 分段滑块（备忘录 / 待办切换），扁平化设计。
 *
 * 实现要点：
 * - 轨道：实色填充 + 主题色染色带，无内阴影 / 内高光 / 描边
 * - 滑块：实色填充 + 主题色染色，无渐变 / 高光 / 光斑 / 描边，仅保留很轻的投影区分层级
 * - 所有层都用 drawRoundRect 自身裁剪到胶囊形，避免 clip 把边缘裁掉一半
 * - 拖动跟手 snapTo，松手 animateTo 到位（无弹簧回弹，交互更克制）
 *
 * @param selected 当前选中类型，[NoteType.NOTE] 或 [NoteType.TODO]
 * @param onSelected 类型切换回调
 */@Composable
fun LiquidSegmentedSlider(
    selected: Int,
    onSelected: (Int) -> Unit,
    leftLabel: String,
    rightLabel: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,

) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val padDp = 4.dp
    val padPx = with(density) { padDp.toPx() }

    // 归一化偏移 0f..1f：0=左(备忘录)，1=右(代办)
    val animOffset = remember { Animatable(if (selected == NoteType.TODO) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }

    // 液态拉伸：拖动时滑块沿移动方向轻微拉长、纵向轻微压缩，松手后弹簧回弹
    val stretch = remember { Animatable(1f) }
    var stretchPivot by remember { mutableStateOf(0.5f) }

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

    // 轨道配色（扁平化）：实色填充，无内阴影 / 内高光 / 描边
    val trackSurface = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val trackTint = primary.copy(alpha = if (isDark) 0.22f else 0.14f)

    BoxWithConstraints(
        modifier = modifier
            .height(44.dp)
            .drawBehind {
                val h = size.height
                val corner = CornerRadius(h / 2f)

                // 实色轨道
                drawRoundRect(trackSurface, cornerRadius = corner)

                // 实时跟随滑块的主题色染色带：只有滑块覆盖到的地方变色，
                // 随 animOffset 实时移动（拖动 snapTo / 释放 animateTo 均逐帧刷新），无延迟。
                val thumbW = (size.width - 2 * padPx) / 2f
                if (thumbW > 0f) {
                    val bx = padPx + animOffset.value * thumbW
                    drawRoundRect(
                        color = trackTint,
                        topLeft = Offset(bx, 0f),
                        size = Size(thumbW, h),
                        cornerRadius = corner
                    )
                }
            }
            // 裁剪子内容，防止文字/滑块溢出胶囊边界
            .clip(CircleShape)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        // 快速进入拉伸态（无回弹的紧弹簧，模拟被捏住）
                        scope.launch {
                            stretch.animateTo(
                                targetValue = 1.05f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessHigh
                                )
                            )
                        }
                    },
                    onDragEnd = {
                        dragging = false
                        val target = if (animOffset.value > 0.5f) 1f else 0f
                        scope.launch {
                            animOffset.animateTo(target, springSpec)
                            onSelected(if (target > 0.5f) NoteType.TODO else NoteType.NOTE)
                        }
                        // 松手后拉伸弹簧回弹，产生液态弹性
                        scope.launch { stretch.animateTo(1f, springSpec) }
                    },
                    onDragCancel = {
                        dragging = false
                        val target = if (selected == NoteType.TODO) 1f else 0f
                        scope.launch {
                            animOffset.animateTo(target, springSpec)
                            stretch.animateTo(1f, springSpec)
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val maxOffset = (widthPx - 2 * padPx) / 2f
                        if (maxOffset <= 0f) return@detectHorizontalDragGestures
                        // 拉伸锚点跟随移动方向：向右拖锚点在左，向左拖锚点在右
                        stretchPivot = when {
                            dragAmount > 0f -> 0f
                            dragAmount < 0f -> 1f
                            else -> stretchPivot
                        }
                        val next = (animOffset.value + dragAmount / maxOffset)
                            .coerceIn(0f, 1f)
                        scope.launch { animOffset.snapTo(next) }
                    }
                )
            }
    ) {
        val thumbWidthDp = (maxWidth - padDp * 2) / 2
        val thumbWidthPx = with(density) { thumbWidthDp.toPx() }

        // 滑块配色（扁平化）：实色填充 + 主题色，无渐变、无高光、无光斑、无描边
        val thumbSurface = MaterialTheme.colorScheme.surface
        val thumbTint = primary.copy(alpha = if (isDark) 0.28f else 0.18f)
        val thumbElevation = if (isDark) Color.Black.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.10f)

        // 滑块：扁平材质，随手指实时位移。
        // 实色填充 + 主题色，无渐变 / 高光 / 光斑 / 描边；仅保留很轻的投影区分层级。
        // offset 用 lambda 延迟读取 animOffset.value，避免动画每帧触发组合阶段重组。
        Box(
            modifier = Modifier
                .padding(vertical = padDp)
                .offset {
                    IntOffset(
                        (padPx + animOffset.value * thumbWidthPx).roundToInt(),
                        0
                    )
                }
                .width(thumbWidthDp)
                .fillMaxHeight()
                // 拖动时轻微拉伸，松手即恢复（无弹簧回弹，配合扁平化更克制）
                .graphicsLayer {
                    scaleX = stretch.value
                    scaleY = 1f - (stretch.value - 1f) * 0.5f
                    transformOrigin = TransformOrigin(stretchPivot, 0.5f)
                }
                .shadow(
                    elevation = 2.dp,
                    shape = CircleShape,
                    ambientColor = thumbElevation,
                    spotColor = thumbElevation
                )
                .drawBehind {
                    val h = size.height
                    val corner = CornerRadius(h / 2f)

                    // 实色底
                    drawRoundRect(thumbSurface, cornerRadius = corner)

                    // 主题色染色层：覆盖区域明显变色
                    drawRoundRect(thumbTint, cornerRadius = corner)
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
