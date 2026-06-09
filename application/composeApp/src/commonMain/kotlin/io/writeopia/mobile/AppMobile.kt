package io.writeopia.mobile

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.KeyEvent
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.auth.navigation.AuthMenuScreenContent
import io.writeopia.auth.navigation.AuthRegisterScreenContent
import io.writeopia.auth.navigation.AuthResetPasswordScreenContent
import io.writeopia.auth.navigation.ChooseWorkspaceScreenContent
import io.writeopia.common.utils.ALLOW_BACKEND
import io.writeopia.common.utils.AuthMenuInnerNavigationRoute
import io.writeopia.common.utils.AuthMenuRoute
import io.writeopia.common.utils.AuthRegisterRoute
import io.writeopia.common.utils.AuthResetPasswordRoute
import io.writeopia.common.utils.ChooseWorkspaceRoute
import io.writeopia.common.utils.MainAppRoute
import io.writeopia.common.utils.Route
import io.writeopia.common.utils.StartAppRoute
import io.writeopia.common.utils.configuration.LocalPlatform
import io.writeopia.common.utils.configuration.PlatformType
import io.writeopia.common.utils.keyboard.KeyboardCommands
import io.writeopia.common.utils.keyboard.isMultiSelectionTrigger
import io.writeopia.drawing.di.DrawingInjection
import io.writeopia.editor.di.EditorKmpInjector
import io.writeopia.features.search.di.SearchInjection
import io.writeopia.model.AccentColor
import io.writeopia.model.ColorThemeOption
import io.writeopia.navigation.MobileNavigationViewModel
import io.writeopia.navigation.StartScreen
import io.writeopia.notemenu.di.NotesMenuInjection
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
    accentColorState: StateFlow<AccentColor?>,
) {
    val startDestination: Route = if (ALLOW_BACKEND) StartAppRoute else MainAppRoute

    val landscapeMobileFn = @Composable {
        val coroutineScope = rememberCoroutineScope()
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

                KeyboardCommands.isShiftArrowUpEvent(keyEvent) -> {
                    sendEvent(KeyboardEvent.SHIFT_ARROW_UP)
                    false
                }

                KeyboardCommands.isShiftArrowDownEvent(keyEvent) -> {
                    sendEvent(KeyboardEvent.SHIFT_ARROW_DOWN)
                    false
                }

                else -> false
            }
        }

        DesktopApp(
            hasGlobalHeader = false,
            selectionState = selectionState,
            keyboardEventFlow = keyboardEventFlow.filterNotNull(),
            coroutineScope = coroutineScope,
            colorThemeOption = colorThemeState,
            accentColorOption = accentColorState,
            selectColorTheme = uiConfigurationViewModel::changeColorTheme,
            selectAccentColor = uiConfigurationViewModel::changeAccentColor,
            toggleMaxScreen = {},
            navigateToRegister = {
                // Will be handled by the Navigation composable
            },
            navigateToResetPassword = {
                // Will be handled by the Navigation composable
            },
            startDestination = startDestination
        )
    }

    BoxWithConstraints {
        if (maxWidth > maxHeight) {
            CompositionLocalProvider(LocalPlatform provides PlatformType.MOBILE_LANDSCAPE) {
                landscapeMobileFn()
            }
        } else {
            val drawingInjection = DrawingInjection()

            CompositionLocalProvider(LocalPlatform provides PlatformType.MOBILE_PORTRAIT) {
                PortraitMobile(
                    startDestination = startDestination,
                    searchInjector = searchInjection,
                    uiConfigViewModel = uiConfigurationViewModel,
                    notesMenuInjection = notesMenuInjection,
                    editorInjector = editorInjector,
                    navigationViewModel = navigationViewModel,
                    drawingInjection = drawingInjection,
                    onDrawingSaved = { documentId, storyStepId, drawingData ->
                        editorInjector.addDrawingToDocument(documentId, storyStepId, drawingData)
                    },
                    extraEntries = { backStack, sharedTransitionScope ->
                        // Add auth entries via the extraEntries callback
                        AuthEntries(
                            backStack = backStack,
                            colorThemeOption = colorThemeState,
                            toAppNavigation = {
                                backStack.clear()
                                backStack.add(MainAppRoute)
                            }
                        )
                    }
                )
            }
        }
    }
}

/**
 * Auth flow entries for Navigation 3.
 * These are rendered when the corresponding route is active.
 */
@Composable
private fun AuthEntries(
    backStack: NavBackStack<NavKey>,
    colorThemeOption: StateFlow<ColorThemeOption?>,
    toAppNavigation: () -> Unit,
) {
    val currentRoute = backStack.lastOrNull()

    when (currentRoute) {
        is StartAppRoute -> {
            StartScreen(
                backStack = backStack,
                colorTheme = colorThemeOption
            )
        }
        is AuthMenuRoute, is AuthMenuInnerNavigationRoute -> {
            AuthMenuScreenContent(
                backStack = backStack,
                colorThemeOption = colorThemeOption,
                toAppNavigation = toAppNavigation
            )
        }
        is AuthRegisterRoute -> {
            AuthRegisterScreenContent(
                backStack = backStack,
                colorThemeOption = colorThemeOption
            )
        }
        is AuthResetPasswordRoute -> {
            AuthResetPasswordScreenContent(
                backStack = backStack,
                colorThemeOption = colorThemeOption,
                toAppNavigation = toAppNavigation
            )
        }
        is ChooseWorkspaceRoute -> {
            ChooseWorkspaceScreenContent(
                backStack = backStack,
                toAppNavigation = toAppNavigation
            )
        }
        else -> {
            // Other routes are handled by the main Navigation composable
        }
    }
}
