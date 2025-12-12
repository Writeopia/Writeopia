package io.writeopia.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.compose.rememberNavController
import io.writeopia.common.utils.Destinations
import io.writeopia.notemenu.di.UiConfigurationInjector
import io.writeopia.notes.desktop.components.DesktopApp
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sqldelight.database.DatabaseFactory
import io.writeopia.sqldelight.database.driver.DriverFactory
import io.writeopia.sqldelight.di.SqlDelightDaoInjector
import io.writeopia.ui.image.ImageLoadConfig
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        ImageLoadConfig.configImageLoad()
        CreateAppInMemory()
    }
}

@Composable
fun CreateAppInMemory() {
    val coroutineScope = rememberCoroutineScope()
    val selectionState = MutableStateFlow(false)

//    WriteopiaDbInjector.initialize(null)
    RepositoryInjector.initialize(SqlDelightDaoInjector.singleton())
    WriteopiaConnectionInjector.setBaseUrl(
        "https://writeopia.dev"
//                        "http://localhost:8080"
    )

    val uiConfigurationViewModel = UiConfigurationInjector.singleton()
        .provideUiConfigurationViewModel()

    val colorTheme =
        uiConfigurationViewModel.listenForColorTheme { "disconnected_user" }

    val navigationController = rememberNavController()

    val databaseStateFlow = DatabaseFactory.createDatabaseAsState(
        DriverFactory(),
        url = "",
        coroutineScope = coroutineScope
    )

//    val databaseCreation = databaseStateFlow.collectAsState().value

//    when (databaseCreation) {
//        is DatabaseCreation.Complete -> {
//            val database = databaseCreation.writeopiaDb
//
//            DesktopApp(
//                writeopiaDb = database,
//                selectionState = selectionState,
//                colorThemeOption = colorTheme,
//                selectColorTheme = uiConfigurationViewModel::changeColorTheme,
//                coroutineScope = coroutineScope,
//                keyboardEventFlow = MutableStateFlow(KeyboardEvent.IDLE),
//                toggleMaxScreen = {},
//                navigateToRegister = {
//                    navigationController.navigate(
//                        Destinations.AUTH_MENU_INNER_NAVIGATION.id
//                    )
//                },
//                navigateToResetPassword = {
//                    navigationController.navigate(
//                        Destinations.AUTH_RESET_PASSWORD.id
//                    )
//                }
//            )
//        }
//
//        DatabaseCreation.Loading -> {
//            ScreenLoading()
//        }
//    }

    DesktopApp(
        selectionState = selectionState,
        colorThemeOption = colorTheme,
        selectColorTheme = uiConfigurationViewModel::changeColorTheme,
        coroutineScope = coroutineScope,
        keyboardEventFlow = MutableStateFlow(KeyboardEvent.IDLE),
        toggleMaxScreen = {},
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

@Composable
fun ScreenLoading() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}
