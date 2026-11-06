package com.example.notesapp.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import com.example.notesapp.ui.components.YunXMotion
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
    val homeEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (initialState.destination.route == Screen.Editor.route) {
            // 与编辑页滑动退场严格同 spec（同 duration、同曲线）：
            // 首页在被揭示时同步淡入到位，快了慢了都会露出半透明首页
            fadeIn(tween(duration, easing = sharedEasing))
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
    // 编辑页进场：「渐渐打开」——真·尺寸+裁剪展开（expandIn），不是缩放。
    // 用云析同款 M3 Expressive spring（YunXMotion）：200ms 级的尺寸展开 + 更快的淡入。
    // expandIn 内部走 ChangeSize（尺寸 + 裁剪），画面从中间一小块连续铺满全屏，
    // 观感即「渐渐打开」；起点取目标尺寸的 40%，既有明显展开感又不过分夸张。
    val editorEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(YunXMotion.enterFade) +
            expandIn(
                animationSpec = YunXMotion.enterSpatialSize,
                initialSize = { IntSize(it.width * 2 / 5, it.height * 2 / 5) },
                expandFrom = Alignment.Center
            )
    }
    // 退场（返回首页）：与设置页完全一致的水平滑动——编辑页整体向右滑出，
    // 首页静止被揭示。此前多版「缩小+淡出」（0.94/0.88 配不同曲线）真机反馈
    // 始终不理想，改用与二级页相同的滑动退场，观感即「退出设置」的熟悉手感。
    // 注意与 secondaryPopExit 保持同一 spec：同 duration、同曲线、同方向，
    // 两类页面退出去的动作完全统一。
    val editorPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(exitSlideSpec()) { fullWidth -> fullWidth }
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
