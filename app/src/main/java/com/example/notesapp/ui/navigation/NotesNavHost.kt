package com.example.notesapp.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
private val editorExpandEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
fun NotesNavHost(
    navController: NavHostController,
    notesViewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    initialNoteId: Long = 0L,
    onNoteIdConsumed: () -> Unit = {}
) {
    val animSpeed by settingsViewModel.animSpeed.collectAsStateWithLifecycle()
    val duration = animDuration(animSpeed)
    // fade 与 slide 同 duration，避免进入退出速度感受不一致
    val fadeDuration = (duration * 0.6f).toInt().coerceAtLeast(80)
    // 与视频实测对齐：整个开合约 460~540ms，动作柔和、无弹出感
    val editorExpandDuration = (duration + 200).coerceIn(460, 540)
    // 编辑页淡入淡出的起始缩放：以淡化为主，略带"慢慢铺满"的落位感
    val editorScale = 0.88f

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

    // 首页：保持「不移动、被覆盖 / 被揭开」——不要把首页整体卷进缩放/淡出过渡。
    // ⚠️ 踩坑记录（勿删）：v3.7.0 曾给首页加 scaleIn/scaleOut 做"旧页轻缩"，随后用户
    // 反馈"从编辑页返回时底部滑块的上浮动画一直向上飞、落不到位"。把首页侧恢复为
    // None（即 v3.6.9 已确认正常的配置）后即修复。
    // 具体机制属「版本相关、尚未真机验证」：首页整体缩放会与底栏 350ms 的
    // slideInVertically(initialOffsetY = { it }) 在父级变换未收敛时争夺落点；
    // 另有 LifecycleRegistry.addObserver 对已 RESUMED 的 entry 补发 ON_RESUME 的路径，
    // 会让 homeResumeCount 额外自增一次。两条都需要真机 log 才能定论，故不写死是其一。
    // 交叉淡化时长对齐（勿删）：从编辑页返回首页时，首页淡入必须与编辑页退场
    // （editorExpandDuration）同步；否则首页 ~300ms 就全亮、编辑页还在其上缩小
    // 悬浮 200ms+，出现「双重影像」。从设置/回收站返回仍用普通 duration。
    val homeEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (initialState.destination.route == Screen.Editor.route) {
            fadeIn(tween(editorExpandDuration, easing = editorExpandEasing))
        } else {
            fadeIn(tween(duration, easing = sharedEasing))
        }
    }
    val homeExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        ExitTransition.None
    }
    val homePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        EnterTransition.None
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
    // 编辑页：以淡化为主 + 轻微缩落位（对齐参考视频的手感）。
    // scale 与 fade 共用同一时长、同一曲线，结束时透明度恰好归零，
    // 不会有"画面还很大却瞬间消失"的残影。
    val editorEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(editorExpandDuration, easing = editorExpandEasing)) +
            scaleIn(
                animationSpec = tween(editorExpandDuration, easing = editorExpandEasing),
                initialScale = editorScale,
                transformOrigin = TransformOrigin.Center
            )
    }
    val editorPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(editorExpandDuration, easing = editorExpandEasing)) +
            scaleOut(
                animationSpec = tween(editorExpandDuration, easing = editorExpandEasing),
                targetScale = editorScale,
                transformOrigin = TransformOrigin.Center
            )
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
        ) { homeEntry ->
            // 每当首页重新回到 RESUMED（冷启动、或从编辑/设置/回收站返回）就自增，
            // 作为 replayKey 通知 HomeScreen 重播底部栏的上浮入场动画。
            // 之所以不用 entry.id：它在同一次返回栈条目中恒定不变，无法反映"回来了"。
            //
            // ⚠️ 关键去重（v3.7.4，勿删）：LifecycleRegistry 在 addObserver 时，
            // 若目标 lifecycle 已是 RESUMED，会**立刻补发一次 ON_RESUME**。
            // 而本 observer 会随转场期间 LocalLifecycleOwner 变化而被反复重新注册
            // → 一次返回会补发多次 → homeResumeCount 多跳几次 → 底栏入场动画
            // 被连续重播多次，正是用户反馈「底栏一直向上飞、冲过屏顶」的真正成因。
            //
            // 解法：homeResumed 放在 composition 级 remember 里（不是 DisposableEffect
            // 的局部变量），这样 observer 重新注册时标志位依然有效：
            // 只有「从非 RESUMED 变为 RESUMED」才计数，补发的那次会被忽略。
            var homeResumeCount by remember(homeEntry) { mutableIntStateOf(0) }
            var homeResumed by remember(homeEntry) { mutableStateOf(false) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            if (!homeResumed) {
                                homeResumed = true
                                homeResumeCount++
                            }
                        }
                        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                            homeResumed = false
                        }
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            HomeScreen(
                viewModel = notesViewModel,
                settingsViewModel = settingsViewModel,
                replayKey = homeResumeCount,
                onNoteClick = { noteId ->
                    navController.navigate(Screen.Editor.createRoute(noteId))
                },
                onAddClick = {
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
