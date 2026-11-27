package com.example.notesapp.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.notesapp.ui.screens.EditorScreen
import com.example.notesapp.ui.screens.HomeScreen
import com.example.notesapp.ui.screens.SettingsScreen
import com.example.notesapp.ui.screens.TrashScreen
import com.example.notesapp.ui.viewmodel.NotesViewModel
import com.example.notesapp.ui.viewmodel.SettingsViewModel

// animSpeed 0f~1f → duration 450ms~150ms（默认更利落，仍可通过滑块调节）
private fun animDuration(animSpeed: Float): Int =
    (450 - (animSpeed * 300).toInt()).coerceIn(150, 450)

// 统一使用对称缓动曲线（先加速后减速），进入和退出速度感受完全一致
private val sharedEasing = FastOutSlowInEasing

@Composable
fun NotesNavHost(
    navController: NavHostController,
    notesViewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    initialNoteId: Long = 0L,
    onNoteIdConsumed: () -> Unit = {}
) {
    // 编辑页「从哪来回哪去」的展开原点（相对屏幕比例 0..1）。
    // 首页点击卡片时写入卡片中心的屏幕比例；编辑页进场从该点展开，
    // 返回时收回到同一点（与 DSH 模型列表插件的 transform-origin 行为一致）。
    var editorOriginX by remember { mutableFloatStateOf(0.5f) }
    var editorOriginY by remember { mutableFloatStateOf(0.5f) }
    // 被点击卡片的实际尺寸比例（相对全屏），作为展开起始尺寸
    var editorSizeW by remember { mutableFloatStateOf(0.34f) }
    var editorSizeH by remember { mutableFloatStateOf(0.14f) }

    val animSpeed by settingsViewModel.animSpeed.collectAsStateWithLifecycle()
    val duration = animDuration(animSpeed)
    // fade 与 slide 同 duration，避免进入退出速度感受不一致
    val fadeDuration = (duration * 0.6f).toInt().coerceAtLeast(80)

    // 通知点击进入时跳转到对应笔记编辑页（支持冷启动与热启动两种场景）
    LaunchedEffect(initialNoteId) {
        if (initialNoteId > 0L) {
            navController.navigate(Screen.Editor.createRoute(initialNoteId))
            onNoteIdConsumed()
        }
    }

    // 进入退出共用同一缓动曲线和同一 duration，速度完全一致
    val enterSlideSpec = { tween<IntOffset>(duration, easing = sharedEasing) }
    val enterFadeSpec = { tween<Float>(fadeDuration, easing = sharedEasing) }
    val exitSlideSpec = { tween<IntOffset>(duration, easing = sharedEasing) }
    val exitFadeSpec = { tween<Float>(fadeDuration, easing = sharedEasing) }

    // 首页：不移动、被覆盖 / 被揭开——不要给首页整体加缩放或位移。
    // ⚠️ 踩坑记录（勿删）：v3.7.0 给首页加过 scaleIn/scaleOut 做「旧页轻缩」，
    // 用户随即反馈底部滑块上浮动画异常；恢复为 None 后正常。
    // 现在底栏已彻底移除重播机制（v3.11.2），首页侧更没有理由再做整体变换。
    // 首页只在「打开编辑页」时淡出（配合编辑页进场），
    // 「返回首页」时由 homePopEnter 淡回，与编辑页淡出交叉。
    val homeEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(duration, easing = sharedEasing))
    }
    // 打开编辑页时底层首页淡出。用 targetState（去向）判断——
    // v3.10.2 误用了 initialState，前缀永远命中不了 Editor，导致首页从不淡出。
    // 淡出要比编辑页淡入更快（约一半时长），形成 cross-fade。
    val homeExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (targetState.destination.route == Screen.Editor.route) {
            // 底层比上层稍快退场，形成可辨认的交叉淡化（云析同款：底层 fastEffects）
            fadeOut(tween((duration * 0.7f).toInt().coerceIn(120, 320), easing = FastOutSlowInEasing))
        } else {
            ExitTransition.None
        }
    }
    // 关闭编辑页返回时首页淡回。用 initialState（离场页）判断是否为编辑页。
    val homePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (initialState.destination.route == Screen.Editor.route) {
            // 与编辑页退场同 240ms：关闭时编辑页收到 0.04 会露出首页，
            // 首页必须在这 240ms 内淡回到位，否则会看到半透明底。
            fadeIn(tween(460, easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)))
        } else {
            EnterTransition.None
        }
    }
    val homePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        ExitTransition.None
    }

    // 二级页面：进入叠加轻 fade 让边缘更柔和；返回时仅滑动退出，避免页面变透明露出首页显得拖
    val secondaryEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(enterSlideSpec()) { fullWidth -> fullWidth } +
            fadeIn(enterFadeSpec())
    }
    val secondaryExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(exitSlideSpec()) { fullWidth -> -fullWidth } +
            fadeOut(exitFadeSpec())
    }
    val secondaryPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(enterSlideSpec()) { fullWidth -> -fullWidth }
    }
    val secondaryPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(exitSlideSpec()) { fullWidth -> fullWidth }
    }
    // ===== 编辑页打开动画：从被点击卡片的真实矩形「原地展开」到全屏 =====
    // 参考视频（AndVVM 虚拟机详情）：点列表卡片 → 卡片本身平滑放大成详情页，
    // 内容连续过渡。这正是 Material 的「共享元素转场」。
    //
    // 编辑页过渡（v3.11.9）：**从下方滑上来 + 淡入**（真机录屏像素测量确认）。
    // 分工：
    // - 位移与淡化由 EditorScreen 内的 SharedExpandBox 驱动（可精确控制距离/曲线）
    // - NavHost 层只做极轻淡入，**不加位移**，避免两段运动叠加成双重滑动
    // 时长/曲线与 SharedExpandBox 一致：380ms，cubic-bezier(0.2,0,0,1)
    val editorGrowDuration = 380
    val editorGrowEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // 卡片起始矩形（屏幕比例），由首页点击时写入，供 EditorScreen 读取
    var editorStartLeft by remember { mutableFloatStateOf(0.04f) }
    var editorStartTop by remember { mutableFloatStateOf(0.12f) }

    // 进场：极短淡入（仅用于消除首帧闪白），主体位移/淡化由 SharedExpandBox 承担。
    // 用 1ms 而不是 None：None 会让页面在内部动画开始前就完全显示，产生闪现。
    val editorEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(1))
    }
    // ⚠️ 退场不能让 NavHost 再叠一层淡出（v3.12.0）：
    // 内部 SharedExpandBox 已在做「下沉 + 淡到 0」，若这里同时 fadeOut，
    // 两套时长/曲线不同步 → 动画结束后仍残留半透明页面，即「退出有残留」。
    // 因此 NavHost 层退场只保留与内部等长的占位（不加任何视觉变换）。
    // 退场：与进场同样极短，避免与内部「下沉+淡出」叠加造成残留
    val editorPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(1))
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(
            route = Screen.Home.route,
            enterTransition = homeEnter,
            exitTransition = homeExit,
            popEnterTransition = homePopEnter,
            popExitTransition = homePopExit
        ) {
            HomeScreen(
                viewModel = notesViewModel,
                settingsViewModel = settingsViewModel,
                onNoteClick = { noteId, originX, originY, sizeW, sizeH ->
                    editorOriginX = originX
                    editorOriginY = originY
                    editorSizeW = sizeW
                    editorSizeH = sizeH
                    editorStartLeft = (originX - sizeW / 2f).coerceIn(0f, 1f)
                    editorStartTop = (originY - sizeH / 2f).coerceIn(0f, 1f)
                    navController.navigate(Screen.Editor.createRoute(noteId))
                },
                onAddClick = { originX, originY ->
                    editorOriginX = originX
                    editorOriginY = originY
                    editorSizeW = 0.34f
                    editorSizeH = 0.14f
                    navController.navigate(Screen.Editor.createRoute(0L))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onTrashClick = {
                    navController.navigate(Screen.Trash.route)
                }
            )
        }

        composable(
            route = Screen.Editor.route,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
            enterTransition = editorEnter,
            exitTransition = secondaryExit,
            popEnterTransition = secondaryPopEnter,
            popExitTransition = editorPopExit
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getLong("noteId") ?: 0L
            EditorScreen(
                viewModel = notesViewModel,
                noteId = noteId,
                // 共享元素展开起点：被点击卡片在屏幕中的矩形（比例）
                expandFrom = androidx.compose.ui.geometry.Rect(
                    left = editorStartLeft,
                    top = editorStartTop,
                    right = (editorStartLeft + editorSizeW).coerceIn(0f, 1f),
                    bottom = (editorStartTop + editorSizeH).coerceIn(0f, 1f)
                ),
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Settings.route,
            enterTransition = secondaryEnter,
            exitTransition = secondaryExit,
            popEnterTransition = secondaryPopEnter,
            popExitTransition = secondaryPopExit
        ) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Trash.route,
            enterTransition = secondaryEnter,
            exitTransition = secondaryExit,
            popEnterTransition = secondaryPopEnter,
            popExitTransition = secondaryPopExit
        ) {
            TrashScreen(
                viewModel = notesViewModel,
                settingsViewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
