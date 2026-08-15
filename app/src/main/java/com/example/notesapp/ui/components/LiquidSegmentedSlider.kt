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
import androidx.compose.ui.graphics.Brush
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 液态玻璃分段滑块（备忘录 / 待办切换）。
 *
 * 玻璃质感实现要点（避免"塑料感"）：
 * - 轨道（track）做凹陷：顶部内阴影 + 底部内高光 + 边缘亮线，形成下凹的玻璃容纳槽
 * - 滑块（thumb）做凸起：顶部镜面高光 + 内部折射光斑 + 底部反射高光 + 边缘亮线 + 软投影
 * - 所有层都用 drawRoundRect 自身裁剪到胶囊形，配合独立的 border 描边，避免 clip 把描边裁掉一半
 * - 半透明叠加让背景透出，形成玻璃折射观感；不用自身 blur，保证镜面高光锐利
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
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
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

    // 轨道（凹陷玻璃槽）配色
    val trackBase = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)
    val trackTopShadow = if (isDark) Color.Black.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.12f)
    val trackBottomLight = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.30f)
    val trackEdge = if (isDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.60f)
    val trackTint = primary.copy(alpha = if (isDark) 0.16f else 0.12f)

    // 毛玻璃轨道样式：Haze 真实背景模糊 + 半透明磨砂染色
    val trackHazeStyle = HazeStyle(
        tint = if (isDark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.55f),
        blurRadius = 24.dp,
        noiseFactor = 0.12f
    )

    BoxWithConstraints(
        modifier = modifier
            .height(44.dp)
            // 毛玻璃模式：轨道注册为 hazeChild，由背景端 Haze 模糊透出下方内容
            .then(if (hazeState != null) Modifier.hazeChild(hazeState, CircleShape, trackHazeStyle) else Modifier)
            .shadow(
                elevation = 2.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .drawBehind {
                val h = size.height
                val corner = CornerRadius(h / 2f)

                // 基础底色：半透明白，让背景透出（毛玻璃模式下跳过，底色由 Haze 模糊 + tint 提供）
                if (hazeState == null) {
                    drawRoundRect(trackBase, cornerRadius = corner)
                }

                // 顶部内阴影：凹陷感
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(trackTopShadow, Color.Transparent),
                        startY = 0f,
                        endY = h * 0.28f
                    ),
                    cornerRadius = corner
                )

                // 底部内高光：玻璃槽反光
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, trackBottomLight),
                        startY = h * 0.55f,
                        endY = h
                    ),
                    cornerRadius = corner
                )

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
            // 轨道边缘亮线：玻璃槽的边沿高光，画在玻璃层之上
            .border(width = 1.dp, color = trackEdge, shape = CircleShape)
            // 最后裁剪子内容，防止文字/滑块溢出胶囊边界
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

        // 滑块（凸起玻璃）配色：在原基础上叠加主题色，让"覆盖处"呈现明显变色
        val thumbTop = if (isDark) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.78f)
        val thumbBottom = if (isDark) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.36f)
        val thumbSpecular = if (isDark) Color.White.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.95f)
        val thumbShade = if (isDark) Color.Black.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.10f)
        val thumbBottomReflect = if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.40f)
        val thumbEdge = if (isDark) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.90f)
        val thumbGlow = if (isDark) Color.White.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.85f)
        // 滑块覆盖区域的主题色染色，与轨道色带同色，强化"覆盖即变色"
        val thumbTint = primary.copy(alpha = if (isDark) 0.16f else 0.13f)

        // 滑块：液态玻璃材质，随手指实时位移。
        // 用清晰的镜面高光 + 内部折射光斑 + 边缘亮线模拟玻璃，不使用自身模糊以保持高光锐利。
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
                // 液态拉伸：拖动时横向拉长、纵向轻微压缩，锚点随移动方向变化
                .graphicsLayer {
                    scaleX = stretch.value
                    scaleY = 1f - (stretch.value - 1f) * 0.5f
                    transformOrigin = TransformOrigin(stretchPivot, 0.5f)
                }
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.24f)
                )
                .drawBehind {
                    val h = size.height
                    val corner = CornerRadius(h / 2f)

                    // 基础渐变：上亮下暗，模拟玻璃受光
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(thumbTop, thumbBottom),
                            startY = 0f,
                            endY = h
                        ),
                        cornerRadius = corner
                    )

                    // 主题色染色层：覆盖区域明显变色
                    drawRoundRect(thumbTint, cornerRadius = corner)

                    // 底部柔阴影：体积感
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, thumbShade),
                            startY = h * 0.60f,
                            endY = h
                        ),
                        cornerRadius = corner
                    )

                    // 内部折射光斑：模拟液态玻璃对光的折射聚光
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(thumbGlow, thumbGlow.copy(alpha = 0f)),
                            center = Offset(size.width * 0.32f, size.height * 0.18f),
                            radius = size.width * 0.75f
                        ),
                        cornerRadius = corner
                    )

                    // 顶部窄高光：镜面反射
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(thumbSpecular, Color.Transparent),
                            startY = 0f,
                            endY = h * 0.45f
                        ),
                        cornerRadius = corner
                    )

                    // 底部反射高光：玻璃下沿的折射亮线
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, thumbBottomReflect),
                            startY = h * 0.55f,
                            endY = h
                        ),
                        cornerRadius = corner
                    )
                }
                // 边缘亮线：玻璃块边沿，画在玻璃层之上，形成清晰的玻璃轮廓
                .border(width = 1.dp, color = thumbEdge, shape = CircleShape)
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
