package com.example.notesapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 液态侧滑删除 v2（v3.9.3 重写交互模型）。
 *
 * v3.9.2 仍有一个根本问题：卡片只跟随 18%，球和脖子画在卡片内容「上方」，
 * 观感是白球白脖子压在文字上。本版改为与参考视频一致的模型：
 *
 * - **卡片真实滑走**（80% 跟手），让出揭示区；
 * - **圆球钉在屏幕边缘**（左滑→右屏边，右滑→左屏边），从卡片滑开时就地显现；
 * - **粘连脖子只在揭示区内**连接卡片尾缘与圆球：拖得越远拉得越长越细，
 *   超过 64dp 撕裂；Canvas 画在卡片**下层**，球从卡片边缘「钻出来」；
 * - 松手不足阈值：卡片低阻尼弹簧 Q 弹回位，圆球以更低阻尼弹簧贴回卡片边缘、
   被回位的卡片自然盖住——两个弹簧独立wobble，产生粘连拖尾手感；
 * - 超过阈值（卡宽 32%）：触发删除（-1 左滑 / +1 右滑），粒子飘散方向沿用。
 *
 * 所有坐标都被限制在「卡片尾缘 ↔ 屏幕边」之间，几何上不可能越界爆开。
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
    val breakDistancePx = with(density) { 64.dp.toPx() }
    val deleteFraction = 0.32f
    val cardFollow = 0.8f

    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var lastDir by remember { mutableFloatStateOf(-1f) }
    var ballVisible by remember { mutableStateOf(false) }

    // 卡片位移；球心 X 绝对坐标；图标透明度
    val cardShift = remember { Animatable(0f) }
    val ballX = remember { Animatable(0f) }
    val iconAlpha = remember { Animatable(0f) }

    var cardJob by remember { mutableStateOf<Job?>(null) }
    var ballJob by remember { mutableStateOf<Job?>(null) }

    fun restBallCx(dir: Float) = if (dir < 0f) widthPx - ballRadius else ballRadius
    fun edgeBase(dir: Float) = if (dir < 0f) widthPx else 0f

    // 拖动中逐帧：卡片跟手，球钉在屏边
    LaunchedEffect(dragX, dragging) {
        if (dragging) {
            cardShift.snapTo(dragX * cardFollow)
            if (abs(dragX) > 4f) {
                lastDir = if (dragX < 0f) -1f else 1f
                ballX.snapTo(restBallCx(lastDir))
                ballVisible = true
                iconAlpha.snapTo(1f)
            }
        }
    }

    fun release() {
        if (!dragging) return
        dragging = false
        val travelled = abs(dragX)
        val dir = lastDir
        if (travelled >= widthPx * deleteFraction) {
            // 删除：状态复位，回调交由列表移除本卡片（粒子在 HomeScreen 侧）
            dragX = 0f
            ballVisible = false
            scope.launch {
                cardShift.snapTo(0f)
                iconAlpha.snapTo(0f)
            }
            onConfirmDelete(dir.toInt())
        } else {
            // Q 弹回：卡片与圆球各自弹簧（不同阻尼 → 独立 wobble = 粘连拖尾）
            cardJob = scope.launch {
                cardShift.animateTo(
                    0f,
                    spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
                )
            }
            ballJob = scope.launch {
                // 球贴回卡片边缘外侧 0.35r 处；卡片归位后被卡片盖住
                ballX.animateTo(
                    edgeBase(dir) - dir * 0.35f * ballRadius,
                    spring(dampingRatio = 0.36f, stiffness = Spring.StiffnessMedium)
                )
            }
            scope.launch {
                cardJob?.join()
                ballVisible = false
                iconAlpha.animateTo(0f, tween(120))
                ballX.snapTo(0f)
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        cardJob?.cancel()
                        ballJob?.cancel()
                        dragging = true
                        dragX = 0f
                    },
                    onDragEnd = { release() },
                    onDragCancel = { release() }
                ) { change, dragAmount ->
                    change.consume()
                    dragX = (dragX + dragAmount).coerceIn(-widthPx, widthPx)
                }
            }
    ) {
        // 球与脖子画在卡片「下层」：球从卡片边缘钻出来，永远只出现在揭示区
        if (ballVisible) {
            Canvas(Modifier.fillMaxSize()) {
                drawLiquid(
                    cardShiftX = cardShift.value,
                    ballCx = ballX.value,
                    dir = lastDir,
                    ballRadius = ballRadius,
                    breakDistancePx = breakDistancePx,
                    color = ballTint
                )
            }
        }

        // 卡片本体：80% 跟手滑走
        Box(modifier = Modifier.graphicsLayer { translationX = cardShift.value }) {
            content()
        }

        // 删除图标：叠在揭示区的圆球上（与 Canvas 球同一圆心）
        if (ballVisible) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(if (lastDir < 0f) Alignment.CenterEnd else Alignment.CenterStart)
                        .graphicsLayer {
                            translationX = ballX.value - restBallCx(lastDir)
                            alpha = iconAlpha.value
                        }
                ) {
                    deleteIcon()
                }
            }
        }
    }
}

/**
 * 有界液态粘连：脖子从「卡片尾缘」连到「圆球近侧」，两段三次贝塞尔锥形收窄，
 * 接点由圆球圆面盖住。X 全程夹在 [min(边,球), max(边,球)]，不可能越界。
 */
private fun DrawScope.drawLiquid(
    cardShiftX: Float,
    ballCx: Float,
    dir: Float,
    ballRadius: Float,
    breakDistancePx: Float,
    color: Color
) {
    if (ballCx <= 0f) return
    val cy = size.height / 2f

    // 圆球本体（带轻微拉伸：跟手时沿运动方向略扁，更 Q）
    drawCircle(color = color, radius = ballRadius, center = Offset(ballCx, cy), style = Fill)

    // 卡片尾缘位置（卡片滑移后）
    val cardEdgeX = if (dir < 0f) size.width + cardShiftX else 0f + cardShiftX
    // 球的近侧（朝向卡片）
    val ballNearX = ballCx - dir * ballRadius
    // 脖子长度：卡片边到球近侧
    val gap = abs(ballNearX - cardEdgeX)
    if (gap < ballRadius * 0.2f || gap >= breakDistancePx) return

    val progress = (gap / breakDistancePx).coerceIn(0f, 1f)
    val t1 = ballRadius * (0.92f - 0.30f * progress)   // 卡片侧半宽（收窄）
    val t2 = ballRadius * (0.58f - 0.28f * progress)   // 球侧半宽（由球面盖住）

    val fromX = min(cardEdgeX, ballNearX)
    val toX = max(cardEdgeX, ballNearX)
    val span = (toX - fromX).coerceAtLeast(1f)
    val m1 = fromX + span * 0.35f
    val m2 = fromX + span * 0.70f

    val neck = Path().apply {
        moveTo(fromX, cy - t1)
        cubicTo(m1, cy - t1, m2, cy - t2, toX, cy - t2)
        lineTo(toX, cy + t2)
        cubicTo(m2, cy + t2, m1, cy + t1, fromX, cy + t1)
        close()
    }
    drawPath(neck, color, style = Fill)
}
