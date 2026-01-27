package io.writeopia.mobile

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.KeyEvent
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.writeopia.auth.navigation.authNavigation
import io.writeopia.common.utils.ALLOW_BACKEND
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.configuration.LocalPlatform
import io.writeopia.common.utils.configuration.PlatformType
import io.writeopia.common.utils.keyboard.KeyboardCommands
import io.writeopia.common.utils.keyboard.isMultiSelectionTrigger
import io.writeopia.editor.di.EditorKmpInjector
import io.writeopia.features.search.di.SearchInjection
import io.writeopia.model.ColorThemeOption
import io.writeopia.navigation.MobileNavigationViewModel
import io.writeopia.navigation.startScreen
import io.writeopia.notemenu.di.NotesMenuInjection
import io.writeopia.notemenu.navigation.navigateToNotes
import io.writeopia.notes.desktop.components.DesktopApp
import io.writeopia.ui.keyboard.KeyboardEvent
import io.writeopia.viewmodel.UiConfigurationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@Composable
fun AppMobile(
    navigationViewModel: MobileNavigationViewModel,
    editorInjector: EditorKmpInjector,
    notesMenuInjection: NotesMenuInjection,
    searchInjection: SearchInjection,
    uiConfigurationViewModel: UiConfigurationViewModel,
    colorThemeState: StateFlow<ColorThemeOption?>,
    navController: NavHostController,
) {
    val landspaceModileFn = @Composable {
        val coroutineScope = rememberCoroutineScope()
        NavHost(
            navController = navController,
            startDestination = if (ALLOW_BACKEND) {
                Destinations.START_APP.id
            } else {
                Destinations.MAIN_APP.id
            }
        ) {
            val selectionState = MutableStateFlow(false)
            val keyboardEventFlow = MutableStateFlow<KeyboardEvent?>(null)
            val sendEvent = { keyboardEvent: KeyboardEvent ->
                coroutineScope.launch(Dispatchers.Default) {
                    keyboardEventFlow.tryEmit(keyboardEvent)
                    delay(20)
                    keyboardEventFlow.tryEmit(KeyboardEvent.IDLE)
                }
            }

            val handleKeyboardEvent: (KeyEvent) -> Boolean = { keyEvent ->
                selectionState.value = keyEvent.isMultiSelectionTrigger()

                when {
                    KeyboardCommands.isDeleteEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.DELETE)
                        false
                    }

                    KeyboardCommands.isSelectAllEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.SELECT_ALL)
                        false
                    }

                    KeyboardCommands.isBoxEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.BOX)
                        false
                    }

                    KeyboardCommands.isBoldEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.BOLD)
                        false
                    }

                    KeyboardCommands.isItalicEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.ITALIC)
                        false
                    }

                    KeyboardCommands.isUnderlineEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.UNDERLINE)
                        false
                    }

                    KeyboardCommands.isLinkEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.LINK)
                        false
                    }

                    KeyboardCommands.isLocalSaveEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.LOCAL_SAVE)
                        false
                    }

                    KeyboardCommands.isCopyEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.COPY)
                        false
                    }

                    KeyboardCommands.isCutEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.CUT)
                        false
                    }

                    KeyboardCommands.isQuestionEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.AI_QUESTION)
                        false
                    }

                    KeyboardCommands.isCancelEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.CANCEL)
                        true
                    }

                    KeyboardCommands.isAcceptAiEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.ACCEPT_AI)
                        false
                    }

                    KeyboardCommands.isUndoKeyboardEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.UNDO)
                        false
                    }

                    KeyboardCommands.isRedoKeyboardEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.REDO)
                        false
                    }
                    KeyboardCommands.isEquationEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.EQUATION)
                        false
                    }

                    KeyboardCommands.isSearchEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.SEARCH)
                        false
                    }

                    KeyboardCommands.isListEvent(keyEvent) -> {
                        sendEvent(KeyboardEvent.LIST)
                        false
                    }

                    else -> false
                }
            }

            startScreen(navController, colorThemeState)

            composable(route = Destinations.MAIN_APP.id) {
                DesktopApp(
                    hasGlobalHeader = false,
                    selectionState = selectionState,
                    keyboardEventFlow = keyboardEventFlow.filterNotNull(),
                    coroutineScope = coroutineScope,
                    colorThemeOption = colorThemeState,
                    selectColorTheme =
                        uiConfigurationViewModel::changeColorTheme,
                    toggleMaxScreen = {},
                    navigateToRegister = {
                        navController.navigate(
                            Destinations.AUTH_MENU_INNER_NAVIGATION.id
                        )
                    },
                    navigateToResetPassword = {
                        navController.navigate(
                            Destinations.AUTH_RESET_PASSWORD.id
                        )
                    }
                )
            }

            authNavigation(
                navController = navController,
                colorThemeOption = colorThemeState
            ) {
                navController.navigate(Destinations.MAIN_APP.id)
            }

            composable(route = Destinations.SEARCH.id) {
                navController.navigate(Destinations.MAIN_APP.id)
            }

            composable(route = Destinations.NOTIFICATIONS.id) {
                navController.navigate(Destinations.MAIN_APP.id)
            }

            composable(route = Destinations.ACCOUNT.id) {
                navController.navigate(Destinations.MAIN_APP.id)
            }

            composable(
                route = "${Destinations.EDITOR.id}/{noteId}/{noteTitle}",
                arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { intSize -> intSize }
                    )
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { intSize -> intSize }
                    )
                }
            ) { backStackEntry ->
                navController.navigate(Destinations.MAIN_APP.id)
            }

            composable(
                route = "${Destinations.EDITOR.id}/{parentFolderId}",
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { intSize -> intSize }
                    )
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { intSize -> intSize }
                    )
                }
            ) { backStackEntry ->
                navController.navigate(Destinations.MAIN_APP.id)
            }
        }
    }

    BoxWithConstraints {
        if (maxWidth > maxHeight) {
            CompositionLocalProvider(LocalPlatform provides PlatformType.MOBILE_LANDSCAPE) {
                landspaceModileFn()
            }
        } else {
            CompositionLocalProvider(LocalPlatform provides PlatformType.MOBILE_PORTRAIT) {
                PortraitMobile(
                    startDestination = Destinations.START_APP.id,
                    navController = navController,
                    searchInjector = searchInjection,
                    uiConfigViewModel = uiConfigurationViewModel,
                    notesMenuInjection = notesMenuInjection,
                    editorInjector = editorInjector,
                    navigationViewModel = navigationViewModel,
                ) {
                    startScreen(navController, colorThemeState)

                    authNavigation(navController, colorThemeState) {
                        navController.navigateToNotes(NotesNavigation.Root)
                    }
                }
            }
        }
    }
}
