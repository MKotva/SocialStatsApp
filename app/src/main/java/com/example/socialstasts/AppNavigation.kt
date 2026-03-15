package com.example.socialstasts

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.socialstasts.models.MainViewModelFactory
import com.example.socialstasts.composables.AccountViewRoute
import com.example.socialstasts.composables.CreatePostRoute
import com.example.socialstasts.composables.MainRoute

object Destinations {
    const val MAIN = "main"
    const val CREATE = "create_post"
    const val ACCOUNT = "account"

    fun createPost(selectedAccName: String? = null): String {
        return if (selectedAccName.isNullOrBlank()) {
            CREATE
        } else {
            "$CREATE?selectedAccName=${Uri.encode(selectedAccName)}"
        }
    }
    fun account(name: String): String = "$ACCOUNT/${Uri.encode(name)}"
}

@Composable
fun AppNavigation(appContainer: AppContainer) {
    val controller = rememberNavController()
    val fac = remember(appContainer) { MainViewModelFactory(appContainer) }

    NavHost(
        navController = controller,
        startDestination = Destinations.MAIN
    ) {
        composable(Destinations.MAIN) {
            MainRoute(
                vm = viewModel(factory = fac),
                onNewPostClick = { controller.navigate(Destinations.createPost()) },
                onAccountClick = { controller.navigate(Destinations.account(it)) }
            )
        }

        composable(
            route = "${Destinations.CREATE}?selectedAccName={selectedAccName}",
            arguments = listOf(
                navArgument("selectedAccName") {
                    nullable = true
                    defaultValue = null
                    type = NavType.StringType
                }
            )
        ) { stackEntry ->
            CreatePostRoute(
                selectedAccName = stackEntry.arguments?.getString("selectedAccName"),
                repo = appContainer.repo,
                onBack = { controller.popBackStack() }
            )
        }

        composable(
            route = "${Destinations.ACCOUNT}/{accountName}",
            arguments = listOf(navArgument("accountName") { type = NavType.StringType })
        ) { stackEntry ->
            AccountViewRoute(
                accName = stackEntry.arguments?.getString("accountName").orEmpty(),
                repo = appContainer.repo,
                onBack = { controller.popBackStack() },
                onNewPostClick = { controller.navigate(Destinations.createPost(it)) }
            )
        }
    }
}