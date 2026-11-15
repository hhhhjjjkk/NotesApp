package com.example.notesapp.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
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
            fadeIn(tween(300, easing = FastOutSlowInEasing))
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
    // ===== 编辑页（打开/关闭备忘录）=====
    // ⚠️ 为什么纯淡入看不见（v3.10.2/v3.10.3 用户连续反馈「打开没动画」）：
    // 首页与编辑页的 Scaffold 背景色是**同一个** colorScheme.background，
    // 淡入时底下透出来的还是同色背景 —— 纯 alpha 变化在视觉上等于「什么都没发生」。
    // 所以进场必须带**真实的空间变化**：轻微缩放（0.92 → 1.0）+ 淡入。
    // 缩放过程中编辑页边缘会露出底层首页内容，动画必然可辨。
    // 时长跟随设置里的「动画速度」（duration = 150~450ms）。
    val pageCloseFade = tween<Float>(300, easing = FastOutSlowInEasing)
    val editorEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(duration, easing = sharedEasing)) +
            scaleIn(
                animationSpec = tween((duration + 60).coerceAtMost(520), easing = sharedEasing),
                initialScale = 0.92f,
                transformOrigin = TransformOrigin.Center
            )
    }
    val editorPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(pageCloseFade)
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
