package io.writeopia.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.kdroidfilter.platformtools.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.platformtools.darkmodedetector.windows.setWindowsAdaptiveTitleBar
import io.writeopia.auth.navigation.authNavigation
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.keyboard.KeyboardCommands
import io.writeopia.common.utils.keyboard.isMultiSelectionTrigger
import io.writeopia.common.utils.ui.GlobalToastBox
import io.writeopia.model.isDarkTheme
import io.writeopia.navigation.ScreenLoading
import io.writeopia.navigation.startScreen
import io.writeopia.notemenu.di.UiConfigurationInjector
import io.writeopia.notes.desktop.components.DesktopApp
import io.writeopia.resources.CommonImages
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sqldelight.database.DatabaseCreation
import io.writeopia.sqldelight.database.DatabaseFactory
import io.writeopia.sqldelight.database.driver.DriverFactory
import io.writeopia.sqldelight.di.WriteopiaDbInjector
import io.writeopia.theme.WrieopiaTheme
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.image.ImageLoadConfig
import io.writeopia.ui.keyboard.KeyboardEvent
import io.writeopia.common.utils.ALLOW_BACKEND
import io.writeopia.common.utils.configuration.LocalPlatform
import io.writeopia.common.utils.configuration.PlatformType
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sqldelight.di.SqlDelightDaoInjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.io.File

private const val APP_DIRECTORY = ".writeopia"
private const val DB_VERSION = 1

fun main() = application {
    App()
}

@Composable
private fun ApplicationScope.App(onCloseRequest: () -> Unit = ::exitApplication) {
    ImageLoadConfig.configImageLoad()

    val coroutineScope = rememberCoroutineScope()

    val homeDirectory: String = System.getProperty("user.home")
    val appDirectory = File(homeDirectory, APP_DIRECTORY)

    val dbName = "writeopia_$DB_VERSION.db"
    val dbPath = "$appDirectory${File.separator}$dbName"
    val url = "jdbc:sqlite:$dbPath"

    if (!appDirectory.exists()) {
        appDirectory.mkdirs()
    }

    val databaseStateFlow = DatabaseFactory.createDatabaseAsState(
        DriverFactory(),
        url = url,
        coroutineScope = coroutineScope
    )

    val selectionState = MutableStateFlow(false)
    val keyboardEventFlow = MutableStateFlow<KeyboardEvent?>(null)
    val sendEvent = { keyboardEvent: KeyboardEvent ->
        coroutineScope.launch(Dispatchers.IO) {
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

    val windowState = rememberWindowState(
        width = 1100.dp,
        height = 800.dp,
        position = WindowPosition(Alignment.Center)
    )

    val topDoubleBarClick = {
        if (windowState.placement == WindowPlacement.Floating) {
            windowState.placement = WindowPlacement.Maximized
        } else {
            windowState.placement = WindowPlacement.Floating
        }
    }

    val appFn = @Composable {
        when (val databaseState = databaseStateFlow.collectAsState().value) {
            is DatabaseCreation.Complete -> {
                val database = databaseState.writeopiaDb

                WriteopiaDbInjector.initialize(database)
                RepositoryInjector.initialize(SqlDelightDaoInjector.singleton())
                WriteopiaConnectionInjector.setBaseUrl(
                    "https://writeopia.dev"
//                        "http://localhost:8080"
                )

                val uiConfigurationInjector = UiConfigurationInjector.singleton()

                val uiConfigurationViewModel = uiConfigurationInjector
                    .provideUiConfigurationViewModel()

                val colorTheme =
                    uiConfigurationViewModel.listenForColorTheme { "disconnected_user" }

                val navigationController = rememberNavController()

                WrieopiaTheme(darkTheme = colorTheme.value.isDarkTheme()) {
                    GlobalToastBox(
                        modifier = Modifier
                            .background(WriteopiaTheme.colorScheme.globalBackground)
                    ) {
                        NavHost(
                            navController = navigationController,
                            startDestination = if (ALLOW_BACKEND) {
                                Destinations.START_APP.id
                            } else {
                                Destinations.MAIN_APP.id
                            }
                        ) {
                            startScreen(navigationController, colorTheme)

                            composable(route = Destinations.MAIN_APP.id) {
                                DesktopApp(
                                    selectionState = selectionState,
                                    keyboardEventFlow = keyboardEventFlow.filterNotNull(),
                                    coroutineScope = coroutineScope,
                                    colorThemeOption = colorTheme,
                                    selectColorTheme =
                                        uiConfigurationViewModel::changeColorTheme,
                                    toggleMaxScreen = topDoubleBarClick,
                                    navigateToRegister = {
                                        navigationController.navigate(
                                            Destinations.AUTH_MENU_INNER_NAVIGATION.id
                                        )
                                    },
                                    navigateToResetPassword = {
                                        navigationController.navigate(
                                            Destinations.AUTH_RESET_PASSWORD.id
                                        )
                                    }
                                )
                            }

                            authNavigation(
                                navController = navigationController,
                                colorThemeOption = colorTheme
                            ) {
                                navigationController.navigate(Destinations.MAIN_APP.id)
                            }
                        }
                    }
                }
            }

            DatabaseCreation.Loading -> {
                ScreenLoading()
            }
        }
    }

    Window(
        onCloseRequest = onCloseRequest,
        title = "",
        state = windowState,
        onKeyEvent = handleKeyboardEvent,
        icon = CommonImages.logo()
    ) {
        //  Synchronize the topbar on Windows OS with the System Theme
        window.setWindowsAdaptiveTitleBar(
            dark = isSystemInDarkMode()
            // TODO Syncrhonize with the selected Theme by the user
        )

        Box(Modifier.fillMaxSize()) {
            LaunchedEffect(window.rootPane) {
                with(window.rootPane) {
                    putClientProperty("apple.awt.transparentTitleBar", true)
                    putClientProperty("apple.awt.fullWindowContent", true)
                }
            }

            CompositionLocalProvider(LocalPlatform provides PlatformType.DESKTOP) {
                appFn()
            }
        }
    }
}
