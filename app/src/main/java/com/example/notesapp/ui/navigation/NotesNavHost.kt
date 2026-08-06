package com.example.notesapp.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    initialNoteId: Long = 0L
) {
    val animSpeed by settingsViewModel.animSpeed.collectAsStateWithLifecycle()
    val duration = animDuration(animSpeed)
    // fade 与 slide 同 duration，避免进入退出速度感受不一致
    val fadeDuration = (duration * 0.6f).toInt().coerceAtLeast(80)

    // 通知点击进入时跳转到对应笔记编辑页
    LaunchedEffect(initialNoteId) {
        if (initialNoteId > 0L) {
            navController.navigate(Screen.Editor.createRoute(initialNoteId))
        }
    }

    // 进入退出共用同一缓动曲线和同一 duration，速度完全一致
    val enterSlideSpec = { tween<IntOffset>(duration, easing = sharedEasing) }
    val enterFadeSpec = { tween<Float>(fadeDuration, easing = sharedEasing) }
    val exitSlideSpec = { tween<IntOffset>(duration, easing = sharedEasing) }
    val exitFadeSpec = { tween<Float>(fadeDuration, easing = sharedEasing) }

    // 首页：不移动，被覆盖 / 被揭开，避免割裂感
    val homeEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(duration, easing = sharedEasing))
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
            enterTransition = secondaryEnter,
            exitTransition = secondaryExit,
            popEnterTransition = secondaryPopEnter,
            popExitTransition = secondaryPopExit
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
