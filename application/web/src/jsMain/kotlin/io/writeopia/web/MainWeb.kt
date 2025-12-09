package io.writeopia.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import io.writeopia.notemenu.di.UiConfigurationInjector
import io.writeopia.notes.desktop.components.DesktopApp
import io.writeopia.ui.image.ImageLoadConfig
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skiko.wasm.onWasmReady
import androidx.navigation.compose.rememberNavController
import io.writeopia.common.utils.Destinations
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sqldelight.di.SqlDelightDaoInjector

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    onWasmReady {
        CanvasBasedWindow(title = "Writeopia") {
            ImageLoadConfig.configImageLoad()
            CreateAppInMemory()
        }
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
