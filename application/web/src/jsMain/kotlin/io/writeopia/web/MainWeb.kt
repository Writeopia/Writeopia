package io.writeopia.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.writeopia.auth.navigation.authNavigation
import io.writeopia.auth.navigation.startScreen
import io.writeopia.common.utils.ALLOW_BACKEND
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.configuration.LocalPlatform
import io.writeopia.common.utils.configuration.PlatformType
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.editor.features.site.ui.SiteScreen
import io.writeopia.editor.features.site.viewmodel.SiteViewModel
import io.writeopia.model.AccentColor
import io.writeopia.model.isDarkTheme
import io.writeopia.global.shell.di.SideMenuKmpInjector
import io.writeopia.notemenu.di.NotesMenuWebInjection
import io.writeopia.notemenu.di.UiConfigurationInjector
import io.writeopia.notes.desktop.components.DesktopApp
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.genai.di.GenAiInjection
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sqldelight.di.SqlDelightDaoInjector
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.drawer.factory.DefaultDrawersJs
import io.writeopia.ui.image.ImageLoadConfig
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

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
    // Use the current origin for API calls (works for both app.writeopia.io and localhost)
    val baseUrl = window.location.origin
    WriteopiaConnectionInjector.setBaseUrl(baseUrl)

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

@OptIn(ExperimentalBrowserHistoryApi::class)
@Composable
fun CreateAppInMemory() {
    val coroutineScope = rememberCoroutineScope()
    val selectionState = MutableStateFlow(false)
    val keyboardEventFlow = MutableStateFlow<KeyboardEvent?>(null)

    RepositoryInjector.initialize(SqlDelightDaoInjector.singleton())
    val baseUrl = window.location.origin
    WriteopiaConnectionInjector.setBaseUrl(baseUrl)
    WriteopiaConnectionInjector.setDisableWebsocket(true)

    // Initialize GenAI (Gemini) for the webapp
    val authRepository = AuthCoreInjectionNeo.singleton().provideAuthRepository()
    GenAiInjection.initialize(
        baseUrl = baseUrl,
        getAuthToken = authRepository::getAuthToken
    )

    val uiConfigurationViewModel = UiConfigurationInjector.singleton()
        .provideUiConfigurationViewModel()

    val colorTheme =
        uiConfigurationViewModel.listenForColorTheme { "disconnected_user" }

    val accentColor =
        uiConfigurationViewModel.listenForAccentColor { "disconnected_user" }

    val accentColorValue by accentColor.collectAsState()

    val navigationController = rememberNavController()

    // Bind navigation to browser history for back/forward button support
    LaunchedEffect(navigationController) {
        navigationController.bindToBrowserNavigation()
    }

    CompositionLocalProvider(LocalPlatform provides PlatformType.WEB) {
        WriteopiaTheme(
            darkTheme = colorTheme.value.isDarkTheme(),
            accentColor = accentColorValue ?: AccentColor.PURPLE
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                    startScreen(navigationController, colorTheme, isWeb = true)

                    composable(route = Destinations.MAIN_APP.id) {
                        DesktopApp(
                            selectionState = selectionState,
                            keyboardEventFlow = keyboardEventFlow.filterNotNull(),
                            coroutineScope = coroutineScope,
                            colorThemeOption = colorTheme,
                            accentColorOption = accentColor,
                            selectColorTheme = uiConfigurationViewModel::changeColorTheme,
                            selectAccentColor = uiConfigurationViewModel::changeAccentColor,
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
                                    popUpTo(navigationController.graph.startDestinationId) {
                                        inclusive = true
                                    }
                                }
                            },
                            notesMenuInjection = NotesMenuWebInjection.singleton(),
                            sideMenuInjector = SideMenuKmpInjector(
                                useBackendOnly = true,
                                menuItemsRepository = NotesMenuWebInjection.singleton().provideMenuItemsRepository()
                            ),
                        )
                    }

                    authNavigation(
                        navController = navigationController,
                        colorThemeOption = colorTheme,
                        isWeb = true
                    ) {
                        navigationController.navigate(Destinations.MAIN_APP.id)
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenLoading() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}
