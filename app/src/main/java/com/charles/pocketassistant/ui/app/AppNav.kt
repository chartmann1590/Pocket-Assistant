package com.charles.pocketassistant.ui.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.charles.pocketassistant.ads.AdManager
import com.charles.pocketassistant.ads.BannerAd
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
import com.charles.pocketassistant.ui.handwriting.HandwritingScreen
import com.charles.pocketassistant.ui.rewards.RewardsScreen
import com.charles.pocketassistant.ui.settings.SettingsScreen
import com.charles.pocketassistant.ui.tasks.TasksScreen

@Composable
fun AppNav(
    pickImage: () -> Unit,
    pickPdf: () -> Unit,
    scanDocument: () -> Unit,
    importViewModel: ImportViewModel,
    adManager: AdManager
) {
    val nav = rememberNavController()
    val appStateVm: AppStateViewModel = hiltViewModel()
    val appState by appStateVm.onboardingComplete.collectAsState()
    val completedItemId by importViewModel.completedItemId.collectAsState()
    val startDestination = if (appState.onboardingComplete) "home" else "onboarding"
    val activity = LocalContext.current as? Activity

    LaunchedEffect(completedItemId) {
        val itemId = completedItemId ?: return@LaunchedEffect
        // Show interstitial after import processing completes
        activity?.let { adManager.showInterstitial(it) }
        nav.navigate("detail/$itemId")
        importViewModel.consumeCompletedItemNavigation()
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
    NavHost(navController = nav, startDestination = startDestination, modifier = Modifier.weight(1f)) {
        composable("onboarding") { OnboardingScreen(nav) }
        composable("home") { HomeScreen(nav, importViewModel) }
        composable("import") {
            ImportScreen(
                onCamera = pickImage,
                onGallery = pickImage,
                onFile = pickPdf,
                onScan = scanDocument,
                onHandwrite = { nav.navigate("handwriting") },
                onPaste = { importViewModel.importText(it) }
            )
        }
        composable("assistant") { AssistantScreen(nav) }
        composable("tasks") { TasksScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
        composable("rewards") { RewardsScreen(nav) }
        composable("handwriting") {
            HandwritingScreen(
                nav = nav,
                onTextRecognized = { text ->
                    importViewModel.importText(text, sourceApp = "Handwriting")
                    nav.popBackStack()
                }
            )
        }
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
    BannerAd(adManager = adManager)
    }
}
