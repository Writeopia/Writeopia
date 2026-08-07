package io.writeopia.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import io.writeopia.common.utils.Destinations
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.editor.features.site.ui.SiteScreen
import io.writeopia.editor.features.site.viewmodel.SiteViewModel
import io.writeopia.model.AccentColor
import io.writeopia.model.isDarkTheme
import io.writeopia.notemenu.di.UiConfigurationInjector
import io.writeopia.notes.desktop.components.DesktopApp
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sqldelight.di.SqlDelightDaoInjector
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.drawer.factory.DefaultDrawersJs
import io.writeopia.ui.image.ImageLoadConfig
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        ImageLoadConfig.configImageLoad()

        val path = window.location.pathname

        if (path.startsWith("/site/")) {
            val documentId = path.removePrefix("/site/").takeWhile { it != '/' }
            if (documentId.isNotBlank()) {
                CreateSiteView(documentId)
            } else {
                CreateAppInMemory()
            }
        } else {
            CreateAppInMemory()
        }
    }
}

@Composable
fun CreateSiteView(documentId: String) {
    RepositoryInjector.initialize(SqlDelightDaoInjector.singleton())
    WriteopiaConnectionInjector.setBaseUrl("https://writeopia.io")

    val connectionInjector = WriteopiaConnectionInjector.singleton()

    val uiConfigurationViewModel = UiConfigurationInjector.singleton()
        .provideUiConfigurationViewModel()

    val colorTheme = uiConfigurationViewModel.listenForColorTheme { "disconnected_user" }
    val accentColor = uiConfigurationViewModel.listenForAccentColor { "disconnected_user" }

    val colorThemeValue by colorTheme.collectAsState()
    val accentColorValue by accentColor.collectAsState()

    val siteViewModel: SiteViewModel = viewModel {
        val documentsApi = DocumentsApi(
            client = connectionInjector.httpClient(),
            baseUrl = connectionInjector.baseUrl()
        )
        SiteViewModel(documentsApi)
    }

    WriteopiaTheme(
        darkTheme = colorThemeValue.isDarkTheme(),
        accentColor = accentColorValue ?: AccentColor.PURPLE
    ) {
        SiteScreen(
            documentId = documentId,
            isDarkTheme = colorThemeValue.isDarkTheme(),
            siteViewModel = siteViewModel,
            drawersFactory = DefaultDrawersJs
        )
    }
}

@Composable
fun CreateAppInMemory() {
    val coroutineScope = rememberCoroutineScope()
    val selectionState = MutableStateFlow(false)

    RepositoryInjector.initialize(SqlDelightDaoInjector.singleton())
    WriteopiaConnectionInjector.setBaseUrl(
        "https://writeopia.io"
    )

    val uiConfigurationViewModel = UiConfigurationInjector.singleton()
        .provideUiConfigurationViewModel()

    val colorTheme =
        uiConfigurationViewModel.listenForColorTheme { "disconnected_user" }

    val accentColor =
        uiConfigurationViewModel.listenForAccentColor { "disconnected_user" }

    val navigationController = rememberNavController()

    DesktopApp(
        selectionState = selectionState,
        colorThemeOption = colorTheme,
        accentColorOption = accentColor,
        selectColorTheme = uiConfigurationViewModel::changeColorTheme,
        selectAccentColor = uiConfigurationViewModel::changeAccentColor,
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
        },
        navigateToChooseWorkspace = {
            navigationController.navigate(Destinations.START_APP.id) {
                popUpTo(navigationController.graph.startDestinationId) { inclusive = true }
            }
        }
    )
}

@Composable
fun ScreenLoading() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}
