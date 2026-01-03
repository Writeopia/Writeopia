package io.writeopia

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import io.writeopia.auth.navigation.authNavigation
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.editor.di.EditorKmpInjector
import io.writeopia.features.search.di.KmpSearchInjection
import io.writeopia.mobile.AppMobile
import io.writeopia.navigation.MobileNavigationViewModel
import io.writeopia.navigation.startScreen
import io.writeopia.notemenu.di.NotesMenuKmpInjection
import io.writeopia.notemenu.di.UiConfigurationInjector
import io.writeopia.notemenu.navigation.navigateToNotes
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sqldelight.database.DatabaseCreation
import io.writeopia.sqldelight.database.DatabaseFactory
import io.writeopia.sqldelight.database.driver.DriverFactory
import io.writeopia.sqldelight.di.SqlDelightDaoInjector
import io.writeopia.sqldelight.di.WriteopiaDbInjector
import io.writeopia.ui.image.ImageLoadConfig
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.writeopia.common.utils.ALLOW_BACKEND
import io.writeopia.common.utils.keyboard.KeyboardCommands
import io.writeopia.common.utils.keyboard.isMultiSelectionTrigger
import io.writeopia.notes.desktop.components.DesktopApp
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@Suppress("FunctionName")
fun MainViewController() = ComposeUIViewController {
    ImageLoadConfig.configImageLoad()

    val coroutine = rememberCoroutineScope()

    val databaseStateFlow = DatabaseFactory.createDatabaseAsState(
        DriverFactory(),
        url = "",
        coroutineScope = coroutine
    )

    when (val databaseState = databaseStateFlow.collectAsState().value) {
        is DatabaseCreation.Complete -> {
            val database = databaseState.writeopiaDb

            WriteopiaDbInjector.initialize(database)
            RepositoryInjector.initialize(SqlDelightDaoInjector.singleton())
            WriteopiaConnectionInjector.setBaseUrl(
                "https://writeopia.dev"
//                        "http://localhost:8080"
            )

            val uiConfigurationInjector = remember { UiConfigurationInjector.singleton() }
            val searchInjection = remember { KmpSearchInjection.singleton() }

            val uiConfigurationViewModel = uiConfigurationInjector
                .provideUiConfigurationViewModel()

            val notesMenuInjection = remember {
                NotesMenuKmpInjection.mobile()
            }

            val editorInjector = EditorKmpInjector.mobile()

            val navigationViewModel = viewModel { MobileNavigationViewModel() }

            val navController = rememberNavController()
            val uiConfigInjection = UiConfigurationInjector.singleton()
            val uiConfigViewModel = uiConfigInjection.provideUiConfigurationViewModel()
            val colorThemeState = uiConfigViewModel.listenForColorTheme { "disconnected_user" }

            BoxWithConstraints {
                if (maxWidth > 800.dp && maxWidth > maxHeight) {
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
                    }
                } else {
                    AppMobile(
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

        DatabaseCreation.Loading -> {
            Box {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
