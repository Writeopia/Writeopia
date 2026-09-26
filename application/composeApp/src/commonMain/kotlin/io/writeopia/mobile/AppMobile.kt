package io.writeopia.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.writeopia.account.ui.AccountDeletionStartedScreen
import io.writeopia.auth.navigation.authNavigation
import io.writeopia.auth.navigation.startScreen
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.configuration.LocalPlatform
import io.writeopia.common.utils.configuration.PlatformType
import io.writeopia.drawing.di.DrawingInjection
import io.writeopia.editor.di.EditorKmpInjector
import io.writeopia.features.search.di.SearchInjection
import io.writeopia.model.AccentColor
import io.writeopia.model.ColorThemeOption
import io.writeopia.navigation.MobileNavigationViewModel
import io.writeopia.notemenu.di.NotesMenuInjection
import io.writeopia.notemenu.navigation.navigateToNotes
import io.writeopia.viewmodel.UiConfigurationViewModel
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AppMobile(
    navigationViewModel: MobileNavigationViewModel,
    editorInjector: EditorKmpInjector,
    notesMenuInjection: NotesMenuInjection,
    searchInjection: SearchInjection,
    uiConfigurationViewModel: UiConfigurationViewModel,
    colorThemeState: StateFlow<ColorThemeOption?>,
    accentColorState: StateFlow<AccentColor?>,
    navController: NavHostController,
) {
    val drawingInjection = DrawingInjection()

    CompositionLocalProvider(LocalPlatform provides PlatformType.MOBILE_PORTRAIT) {
        PortraitMobile(
            startDestination = Destinations.START_APP.id,
            navController = navController,
            searchInjector = searchInjection,
            uiConfigViewModel = uiConfigurationViewModel,
            notesMenuInjection = notesMenuInjection,
            editorInjector = editorInjector,
            navigationViewModel = navigationViewModel,
            drawingInjection = drawingInjection,
            onDrawingSaved = { documentId, storyStepId, drawingData ->
                editorInjector.addDrawingToDocument(documentId, storyStepId, drawingData)
            }
        ) {
            startScreen(navController, colorThemeState)

            authNavigation(navController, colorThemeState) {
                navController.navigateToNotes(NotesNavigation.Root)
            }

            composable(route = Destinations.ACCOUNT_DELETION_STARTED.id) {
                AccountDeletionStartedScreen(
                    logout = {
                        // Resets the nav graph back to START_APP so the login-state
                        // check re-runs and can land on the space-choice screen.
                        navController.navigate(Destinations.START_APP.id) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
