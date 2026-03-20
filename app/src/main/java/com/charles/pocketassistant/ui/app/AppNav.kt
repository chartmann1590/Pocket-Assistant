package com.charles.pocketassistant.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.charles.pocketassistant.ui.AppStateViewModel
import com.charles.pocketassistant.ui.ImportViewModel
import com.charles.pocketassistant.ui.assistant.AssistantScreen
import com.charles.pocketassistant.ui.detail.ItemDetailScreen
import com.charles.pocketassistant.ui.home.HomeScreen
import com.charles.pocketassistant.ui.importing.ImportScreen
import com.charles.pocketassistant.ui.onboarding.OnboardingScreen
import com.charles.pocketassistant.ui.settings.SettingsScreen
import com.charles.pocketassistant.ui.tasks.TasksScreen

@Composable
fun AppNav(
    pickImage: () -> Unit,
    pickPdf: () -> Unit,
    importViewModel: ImportViewModel
) {
    val nav = rememberNavController()
    val appStateVm: AppStateViewModel = hiltViewModel()
    val appState by appStateVm.onboardingComplete.collectAsState()
    val completedItemId by importViewModel.completedItemId.collectAsState()
    val startDestination = if (appState.onboardingComplete) "home" else "onboarding"

    LaunchedEffect(completedItemId) {
        val itemId = completedItemId ?: return@LaunchedEffect
        nav.navigate("detail/$itemId")
        importViewModel.consumeCompletedItemNavigation()
    }

    NavHost(navController = nav, startDestination = startDestination) {
        composable("onboarding") { OnboardingScreen(nav) }
        composable("home") { HomeScreen(nav, pickImage, pickPdf, importViewModel) }
        composable("import") {
            ImportScreen(
                onCamera = pickImage,
                onGallery = pickImage,
                onFile = pickPdf,
                onPaste = { importViewModel.importText(it) }
            )
        }
        composable("assistant") { AssistantScreen(nav) }
        composable("tasks") { TasksScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
        composable(
            route = "detail/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { backStackEntry ->
            ItemDetailScreen(
                nav = nav,
                itemId = backStackEntry.arguments?.getString("itemId").orEmpty()
            )
        }
    }
}
