package com.example.notesapp.ui.navigation

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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.notesapp.ui.screens.EditorScreen
import com.example.notesapp.ui.screens.HomeScreen
import com.example.notesapp.ui.screens.SettingsScreen
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
            // 首页进入：淡入 + 从左侧轻微滑入
            enterTransition = {
                fadeIn(fadeSpec()) +
                    slideInHorizontally(slideSpring) { fullWidth -> -fullWidth / HOME_PARALLAX }
            },
            // 进入二级页时首页退出：淡出 + 向左轻微滑出（视差）
            exitTransition = {
                fadeOut(fadeSpec()) +
                    slideOutHorizontally(slideSpring) { fullWidth -> -fullWidth / HOME_PARALLAX }
            },
            // pop 回首页：淡入 + 从左侧滑入
            popEnterTransition = {
                fadeIn(fadeSpec()) +
                    slideInHorizontally(slideSpring) { fullWidth -> -fullWidth / HOME_PARALLAX }
            }
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
                }
            )
        }

        composable(
            route = Screen.Editor.route,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
            // 进入编辑页：从右侧滑入 + 淡入
            enterTransition = {
                slideInHorizontally(slideSpring) { fullWidth -> fullWidth } +
                    fadeIn(fadeSpec())
            },
            // 离开编辑页（返回首页）：向右滑出 + 淡出
            popExitTransition = {
                slideOutHorizontally(slideSpring) { fullWidth -> fullWidth } +
                    fadeOut(fadeSpec())
            }
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
            // 进入设置页：从右侧滑入 + 淡入
            enterTransition = {
                slideInHorizontally(slideSpring) { fullWidth -> fullWidth } +
                    fadeIn(fadeSpec())
            },
            // 离开设置页（返回首页）：向右滑出 + 淡出
            popExitTransition = {
                slideOutHorizontally(slideSpring) { fullWidth -> fullWidth } +
                    fadeOut(fadeSpec())
            }
        ) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
