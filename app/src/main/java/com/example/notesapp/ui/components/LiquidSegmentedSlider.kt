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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.notesapp.ui.theme.realGlassBlur
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

    // 轨道（凹陷玻璃槽）配色
    val trackBase = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.16f)
    val trackTopShadow = if (isDark) Color.Black.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.07f)
    val trackBottomLight = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.20f)

    BoxWithConstraints(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .drawBehind {
                val h = size.height
                // 基础底色
                drawRect(trackBase)
                // 顶部内阴影：凹陷感
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(trackTopShadow, Color.Transparent),
                        startY = 0f,
                        endY = h * 0.22f
                    )
                )
                // 底部内高光：反光
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, trackBottomLight),
                        startY = h * 0.65f,
                        endY = h
                    )
                )
                // 实时跟随滑块的主题色染色带：只有滑块覆盖到的地方变色，
                // 随 animOffset 实时移动（拖动 snapTo / 释放 animateTo 均逐帧刷新），无延迟。
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

        // 滑块（凸起玻璃）配色：在原基础上叠加主题色，让"覆盖处"呈现明显变色
        val thumbTop = if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.55f)
        val thumbBottom = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.26f)
        val thumbSpecular = if (isDark) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.80f)
        val thumbShade = if (isDark) Color.Black.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.06f)
        // 滑块覆盖区域的主题色染色，与轨道色带同色，强化"覆盖即变色"
        val thumbTint = primary.copy(alpha = if (isDark) 0.10f else 0.08f)

        // 滑块：液态玻璃材质，随手指实时位移
        // realGlassBlur 在 Android 12+ 对玻璃层做真实高斯模糊，告别"塑料感"
        // offset 用 lambda 延迟读取 animOffset.value，避免动画每帧触发组合阶段重组
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
                .shadow(
                    elevation = 5.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.18f)
                )
                .clip(CircleShape)
                .realGlassBlur(8.dp)
                .drawBehind {
                    val h = size.height
                    // 基础渐变：上亮下暗，模拟玻璃受光
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(thumbTop, thumbBottom),
                            startY = 0f,
                            endY = h
                        )
                    )
                    // 主题色染色层：覆盖区域明显变色
                    drawRect(thumbTint)
                    // 顶部窄高光：镜面反射
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(thumbSpecular, Color.Transparent),
                            startY = 0f,
                            endY = h * 0.42f
                        )
                    )
                    // 底部柔阴影：体积感
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, thumbShade),
                            startY = h * 0.55f,
                            endY = h
                        )
                    )
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
