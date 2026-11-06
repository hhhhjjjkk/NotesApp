package com.example.notesapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp

/**
 * 云析（YunX）同款动效参数 —— M3 Expressive MotionScheme 的等价 spring。
 *
 * 核心原则：位移/尺寸 → spatial spec；透明度 → effects spec；进入慢、退出快。
 * 全部 spring，无一处 tween 手填毫秒（系统动画时长缩放为 0 时整体瞬开）。
 */
object YunXMotion {
    /** defaultSpatialSpec：展开 / 位移 / 旋转（进入）—— Float 版 */
    val enterSpatial = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
    /** fastSpatialSpec：收起 / 位移（退出）—— Float 版 */
    val exitSpatial = spring<Float>(dampingRatio = 0.6f, stiffness = 800f)
    /** 同物理参数的 IntSize 版：expandIn/shrinkOut（尺寸动画）专用 */
    val enterSpatialSize = spring<IntSize>(dampingRatio = 0.8f, stiffness = 380f)
    val exitSpatialSize = spring<IntSize>(dampingRatio = 0.6f, stiffness = 800f)
    /** defaultEffectsSpec：淡入 */
    val enterFade = spring<Float>(dampingRatio = 1.0f, stiffness = 1600f)
    /** fastEffectsSpec：淡出 */
    val exitFade = spring<Float>(dampingRatio = 1.0f, stiffness = 3800f)
}

/**
 * 可折叠的二级菜单 / 分组（云析同款动画）。
 *
 * - 内容展开：fadeIn(enterFade) + expandIn(enterSpatial)；收起：fadeOut(exitFade) + shrinkOut(exitSpatial)
 * - 箭头 0°→180° 与内容共用同一条 enterSpatial spring —— 同时开始、同时结束，看起来是一体
 * - 收起后内容移出组合（不是 alpha=0），不要在里面放必须常驻的副作用
 *
 * @param initiallyExpanded 初始展开状态（默认收起，二级菜单的常见预期）
 */
@Composable
fun ExpandableSection(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    headerExtra: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(modifier = modifier.fillMaxWidth()) {
        // 标题行：标题 + 可选附加内容 + 箭头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            headerExtra()
            val rotation by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = YunXMotion.enterSpatial,
                label = "arrow"
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .rotate(rotation)
            )
        }

        // 内容：展开/收起（尺寸+裁剪 与 透明度 两条 transition 叠加）
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(YunXMotion.enterFade) + expandIn(YunXMotion.enterSpatialSize),
            exit = fadeOut(YunXMotion.exitFade) + shrinkOut(YunXMotion.exitSpatialSize)
        ) {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                content()
            }
        }
    }
}
