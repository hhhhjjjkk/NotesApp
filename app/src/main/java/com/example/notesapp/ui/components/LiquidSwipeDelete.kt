package com.example.notesapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 液态侧滑删除（参考鸿蒙通知中心的「圆球跟手 + 粘连 + Q 弹」交互）。
 *
 * v3.9.2 修复：v3.9.0 的 metaball 用 acos/虚拟大圆计算公切点，
 * 且左滑时把卡片边缘取在了错误的一侧（圆球图标却在另一侧），
 * 路径坐标失控后会填充出横跨半屏的巨型墨滴并溢出到相邻卡片。
 * 现改为**有界锥形脖子**：两段贝塞尔从卡片边缘连到圆球中心区，
 * 接点由圆球圆面盖住；所有坐标都在 [edge, ball] 之间，结构上不可能越界。
 *
 * - 圆球 1:1 跟手（左滑从右边缘长出向左走，右滑相反）；
 * - 卡片本体以 18% 阻尼轻微跟随；
 * - 粘连脖子随距离收窄，超过 [breakDistancePx] 断开（只余圆球）；
 * - 松手不足阈值：低阻尼弹簧 Q 弹回；超过：触发 [onConfirmDelete]
 *   （-1 = 向左滑删除，+1 = 向右滑删除）。
 */
@Composable
fun LiquidSwipeDelete(
    enabled: Boolean,
    ballTint: Color,
    deleteIcon: @Composable () -> Unit,
    onConfirmDelete: (direction: Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val ballRadius = with(density) { 22.dp.toPx() }
    val breakDistancePx = with(density) { 56.dp.toPx() }
    val deleteFraction = 0.32f

    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val ballOffset = remember { Animatable(0f) }
    val cardShift = remember { Animatable(0f) }

    var ballJob by remember { mutableStateOf<Job?>(null) }
    var cardJob by remember { mutableStateOf<Job?>(null) }

    fun animateBack() {
        ballJob = scope.launch {
            ballOffset.animateTo(
                0f,
                spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium)
            )
        }
        cardJob = scope.launch {
            cardShift.animateTo(
                0f,
                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    fun release() {
        if (!dragging) return
        dragging = false
        val travelled = abs(dragX)
        if (travelled >= widthPx * deleteFraction) {
            val dir = if (dragX < 0f) -1 else 1
            dragX = 0f
            scope.launch {
                ballOffset.snapTo(0f)
                cardShift.snapTo(0f)
            }
            onConfirmDelete(dir)
        } else {
            animateBack()
        }
    }

    // 拖动期间逐帧同步（dragX 每帧变化都会重启本 effect）
    LaunchedEffect(dragX, dragging) {
        if (dragging) {
            ballOffset.snapTo(dragX)
            cardShift.snapTo(dragX * 0.18f)
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        // cancel 非 suspend，可在手势回调中直接调用
                        ballJob?.cancel()
                        cardJob?.cancel()
                        dragging = true
                        dragX = ballOffset.value
                    },
                    onDragEnd = { release() },
                    onDragCancel = { release() }
                ) { change, dragAmount ->
                    change.consume()
                    dragX = (dragX + dragAmount).coerceIn(-widthPx, widthPx)
                }
            }
    ) {
        // 卡片本体：18% 阻尼跟随，产生「被拖着走一点」的手感
        Box(modifier = Modifier.graphicsLayer { translationX = cardShift.value }) {
            content()
        }

        val bx = ballOffset.value
        if (abs(bx) > 0.5f) {
            val shift = cardShift.value
            Canvas(Modifier.fillMaxSize()) {
                drawLiquid(
                    ballOffsetX = bx,
                    cardShiftX = shift,
                    ballRadius = ballRadius,
                    breakDistancePx = breakDistancePx,
                    color = ballTint
                )
            }
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        // 左滑（bx<0）圆球从右边缘长出；右滑从左边缘
                        .align(if (bx < 0f) Alignment.CenterEnd else Alignment.CenterStart)
                        .graphicsLayer {
                            // 与 Canvas 圆球同一圆心：圆球静止圆心在
                            // edge ∓ ballRadius，再随拖动与卡片位移移动
                            translationX = bx + shift
                        }
                ) {
                    deleteIcon()
                }
            }
        }
    }
}

/**
 * 有界液态粘连：从卡片边缘到圆球画一段锥形脖子（两段三次贝塞尔），
 * 接点半宽 t2 < 圆球半径，由后画的圆球圆面盖住接缝。
 * 全部坐标落在 [min(edge,ball), max(edge,ball)] × [cy±r] 内，不可能越界爆开。
 */
private fun DrawScope.drawLiquid(
    ballOffsetX: Float,
    cardShiftX: Float,
    ballRadius: Float,
    breakDistancePx: Float,
    color: Color
) {
    val dist = abs(ballOffsetX)
    if (dist < 0.5f) return
    val dir = if (ballOffsetX < 0f) -1f else 1f
    val cy = size.height / 2f
    // 卡片内容被推移 cardShift；圆球从内容当时的边缘长出
    val edgeX = (if (dir < 0f) size.width else 0f) + cardShiftX
    val ballCx = edgeX + dir * (ballRadius + dist)
    val ballCy = cy

    // 圆球本体
    drawCircle(color = color, radius = ballRadius, center = Offset(ballCx, ballCy), style = Fill)

    // 粘连脖子：超过断裂距离就只余圆球
    if (dist >= breakDistancePx) return

    val t1 = ballRadius * 1.04f   // 边缘处半宽（略鼓，液感）
    val t2 = ballRadius * 0.72f   // 接球处半宽（收窄，由圆面盖住接点）
    val midX = edgeX + (ballCx - edgeX) * 0.45f

    val neck = Path().apply {
        moveTo(edgeX, ballCy - t1)
        cubicTo(midX, ballCy - t1, ballCx, ballCy - t2, ballCx, ballCy - t2)
        lineTo(ballCx, ballCy + t2)
        cubicTo(midX, ballCy + t1, edgeX, ballCy + t1, edgeX, ballCy + t1)
        close()
    }
    drawPath(neck, color, style = Fill)
}
