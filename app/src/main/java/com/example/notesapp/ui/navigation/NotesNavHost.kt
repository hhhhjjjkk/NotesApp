package com.example.notesapp.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
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

private const val DURATION = 320

// 滑动用 spring：基于物理模型，被打断时会从当前位置/速度平滑继续，不会跳变
private val slideSpring = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

// 首页位移比例（视差效果）
private const val HOME_PARALLAX = 4

private val fadeSpec = { tween<Float>(DURATION, easing = FastOutSlowInEasing) }

// ===== 首页过渡（背景层）=====
// 原则：进入二级页时向左视差退去，返回时从左视差回到原位——从哪退走就从哪回来。
// enter（首次启动 / 新进入首页）：纯淡入，避免无来由的横向滑动
private val homeEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(fadeSpec())
}
// exit（前进离开，退为背景）：向左视差滑出 + 淡出
private val homeExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(fadeSpec()) +
        slideOutHorizontally(slideSpring) { fullWidth -> -fullWidth / HOME_PARALLAX }
}
// popEnter（返回回到首页）：从左视差滑入 + 淡入（与 exit 镜像）
private val homePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(fadeSpec()) +
        slideInHorizontally(slideSpring) { fullWidth -> -fullWidth / HOME_PARALLAX }
}
// popExit（pop 离开首页）：与 exit 一致
private val homePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(fadeSpec()) +
        slideOutHorizontally(slideSpring) { fullWidth -> -fullWidth / HOME_PARALLAX }
}

// ===== 二级页面过渡（Editor / Settings 共用，前景层）=====
// 原则：从右侧滑入覆盖首页，返回时向右滑出回到来处——从哪来回哪去。
// enter（前进进入）：从右满屏滑入 + 淡入
private val secondaryEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(slideSpring) { fullWidth -> fullWidth } + fadeIn(fadeSpec())
}
// exit（前进离开到更深页，退为背景）：向左视差滑出 + 淡出
private val secondaryExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(slideSpring) { fullWidth -> -fullWidth / HOME_PARALLAX } + fadeOut(fadeSpec())
}
// popEnter（返回回到二级页）：从左视差滑入 + 淡入（与 exit 镜像）
private val secondaryPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(slideSpring) { fullWidth -> -fullWidth / HOME_PARALLAX } + fadeIn(fadeSpec())
}
// popExit（返回离开二级页）：向右满屏滑出 + 淡出（与 enter 镜像，从右来回右去）
private val secondaryPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(slideSpring) { fullWidth -> fullWidth } + fadeOut(fadeSpec())
}

@Composable
fun NotesNavHost(
    navController: NavHostController,
    notesViewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
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
