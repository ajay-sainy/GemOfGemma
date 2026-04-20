package com.gemofgemma.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gemofgemma.ui.chat.ChatScreen
import com.gemofgemma.ui.chat.ImageCaptureScreen
import com.gemofgemma.ui.onboarding.OnboardingScreen
import com.gemofgemma.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Chat : Screen("chat")
    data object Capture : Screen("capture")
    data object Settings : Screen("settings")
    data object Onboarding : Screen("onboarding")
}

@Composable
fun NavGraph() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("gemofgemma_prefs", Context.MODE_PRIVATE) }
    val onboardingCompleted = remember { prefs.getBoolean("onboarding_completed", false) }
    val startDest = if (onboardingCompleted) Screen.Chat.route else Screen.Onboarding.route

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDest
    ) {
        composable(Screen.Chat.route) { backStackEntry ->
            val capturedImageBytes by backStackEntry.savedStateHandle
                .getStateFlow<ByteArray?>("captured_image", null)
                .collectAsStateWithLifecycle()

            ChatScreen(
                capturedImageBytes = capturedImageBytes,
                onCapturedImageConsumed = {
                    backStackEntry.savedStateHandle["captured_image"] = null
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToCapture = {
                    navController.navigate(Screen.Capture.route)
                }
            )
        }

        composable(Screen.Capture.route) {
            ImageCaptureScreen(
                onImageCaptured = { bytes ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("captured_image", bytes)
                    navController.popBackStack()
                },
                onDismiss = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onOnboardingComplete = {
                    prefs.edit().putBoolean("onboarding_completed", true).apply()
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
