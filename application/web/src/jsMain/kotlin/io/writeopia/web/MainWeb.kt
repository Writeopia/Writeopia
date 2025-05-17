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

    val uiConfigurationViewModel = UiConfigurationInjector.singleton()
        .provideUiConfigurationViewModel()

    val colorTheme =
        uiConfigurationViewModel.listenForColorTheme { "disconnected_user" }

    DesktopApp(
        selectionState = selectionState,
        colorThemeOption = colorTheme,
        selectColorTheme = uiConfigurationViewModel::changeColorTheme,
        coroutineScope = coroutineScope,
        keyboardEventFlow = MutableStateFlow(KeyboardEvent.IDLE),
        toggleMaxScreen = {},
        navigateToRegister = {}
    )
}
