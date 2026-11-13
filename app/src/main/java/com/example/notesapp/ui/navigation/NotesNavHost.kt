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

    // 首页：保持「不移动、被覆盖 / 被揭开」——不要把首页整体卷进缩放/淡出过渡。
    // ⚠️ 踩坑记录（勿删）：v3.7.0 曾给首页加 scaleIn/scaleOut 做"旧页轻缩"，随后用户
    // 反馈"从编辑页返回时底部滑块的上浮动画一直向上飞、落不到位"。把首页侧恢复为
    // None（即 v3.6.9 已确认正常的配置）后即修复。
    // 具体机制属「版本相关、尚未真机验证」：首页整体缩放会与底栏 350ms 的
    // slideInVertically(initialOffsetY = { it }) 在父级变换未收敛时争夺落点；
    // 另有 LifecycleRegistry.addObserver 对已 RESUMED 的 entry 补发 ON_RESUME 的路径，
    // 会让 homeResumeCount 额外自增一次。两条都需要真机 log 才能定论，故不写死是其一。
    // 交叉淡化时长对齐（勿删）：从编辑页返回首页时，首页淡入必须与编辑页
    // 滑动退场（exitSlideSpec，即普通 duration）严格同 spec；否则首页会先于
    // 编辑页滑完而全亮/或滞后露出半透明页面，出现「双重影像」。
    // 从设置/回收站返回同样用普通 duration，两类页面返回动作已统一。
    // 首页进场（冷启动）：简单淡入即可
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
        ) { homeEntry ->
            // 每当首页重新回到 RESUMED（冷启动、或从编辑/设置/回收站返回）就自增，
            // 作为 replayKey 通知 HomeScreen 重播底部栏的上浮入场动画。
            // 之所以不用 entry.id：它在同一次返回栈条目中恒定不变，无法反映"回来了"。
            //
            // ⚠️ 关键去重（勿删）：LifecycleRegistry 在 addObserver 时，若目标 lifecycle
            // 已是 RESUMED，会**立刻补发一次 ON_RESUME**；而本 observer 会随转场期间
            // LocalLifecycleOwner 变化被反复重新注册 → 一次返回可补发多次 →
            // homeResumeCount 多跳 → 底栏入场动画被连续重播（v3.7.0「一直向上飞」成因）。
            // 单靠 homeResumed 布尔标志不够：重注册过程中夹杂的 ON_PAUSE 会把它清掉。
            // 因此用时间戳防抖：1 秒内多次 ON_RESUME 只计数一次。
            var homeResumeCount by remember(homeEntry) { mutableIntStateOf(0) }
            // 时间戳防抖：转场期间 owner 切换会反复补发 ON_RESUME 且中间夹 ON_PAUSE，
            // 单靠 homeResumed 标志挡不住（标志会被 PAUSE 清掉）。
            // 1 秒内只计数一次，保证一次返回最多重播一次底栏动画。
            var lastResumeAt by remember(homeEntry) { mutableStateOf(0L) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        val now = System.currentTimeMillis()
                        if (now - lastResumeAt >= 1000L) {
                            lastResumeAt = now
                            homeResumeCount++
                        }
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
