package com.example.notesapp.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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

private const val DURATION = 500

private val slideSpec = { tween<IntOffset>(DURATION, easing = FastOutSlowInEasing) }
private val fadeSpec = { tween<Float>(DURATION, easing = FastOutSlowInEasing) }

// ===== 首页：不移动，被覆盖 / 被揭开 =====
private val homeEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(fadeSpec())
}
// 首页被二级页面覆盖时：原地不动，不做任何动画
private val homeExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    ExitTransition.None
}
// 二级页面滑走后首页被揭开：原地不动
private val homePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    EnterTransition.None
}
private val homePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    ExitTransition.None
}

// ===== 二级页面：从右侧滑入覆盖首页，返回时向右滑出 =====
private val secondaryEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(slideSpec()) { fullWidth -> fullWidth }
}
private val secondaryExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(slideSpec()) { fullWidth -> -fullWidth }
}
private val secondaryPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(slideSpec()) { fullWidth -> -fullWidth }
}
private val secondaryPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(slideSpec()) { fullWidth -> fullWidth }
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
