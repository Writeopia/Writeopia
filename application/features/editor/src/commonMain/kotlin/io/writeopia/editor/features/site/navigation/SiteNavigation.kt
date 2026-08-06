package io.writeopia.editor.features.site.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.writeopia.common.utils.Destinations
import io.writeopia.editor.features.site.ui.SiteScreen
import io.writeopia.editor.features.site.viewmodel.SiteViewModel
import io.writeopia.ui.drawer.factory.DrawersFactory

fun NavGraphBuilder.siteNavigation(
    isDarkTheme: Boolean,
    siteViewModelProvider: @Composable () -> SiteViewModel,
    drawersFactory: DrawersFactory
) {
    composable(
        route = "${Destinations.SITE.id}/{documentId}",
        arguments = listOf(
            navArgument("documentId") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val documentId = backStackEntry.savedStateHandle.get<String>("documentId")

        if (documentId != null) {
            val siteViewModel = siteViewModelProvider()

            SiteScreen(
                documentId = documentId,
                isDarkTheme = isDarkTheme,
                siteViewModel = siteViewModel,
                drawersFactory = drawersFactory
            )
        }
    }
}
