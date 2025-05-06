package io.writeopia

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import io.writeopia.auth.core.token.AppBearerTokenHandler
import io.writeopia.auth.di.AuthInjection
import io.writeopia.auth.navigation.authNavigation
import io.writeopia.editor.di.EditorKmpInjector
import io.writeopia.features.search.di.KmpSearchInjection
import io.writeopia.mobile.AppMobile
import io.writeopia.navigation.MobileNavigationViewModel
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.notemenu.di.NotesMenuKmpInjection
import io.writeopia.notemenu.di.UiConfigurationInjector
import io.writeopia.notemenu.navigation.navigateToNotes
import io.writeopia.sdk.network.injector.ConnectionInjector
import io.writeopia.sqldelight.database.DatabaseCreation
import io.writeopia.sqldelight.database.DatabaseFactory
import io.writeopia.sqldelight.database.driver.DriverFactory
import io.writeopia.sqldelight.di.SqlDelightDaoInjector
import io.writeopia.sqldelight.di.WriteopiaDbInjector
import io.writeopia.ui.image.ImageLoadConfig

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

            val uiConfigurationInjector = remember { UiConfigurationInjector.singleton() }
            val sqlDelightDaoInjector = remember { SqlDelightDaoInjector.singleton() }
            val searchInjection = remember { KmpSearchInjection.singleton() }

            val uiConfigurationViewModel = uiConfigurationInjector
                .provideUiConfigurationViewModel()

            val notesMenuInjection = remember {
                NotesMenuKmpInjection.mobile(sqlDelightDaoInjector)
            }

            val connectionInjection =
                remember {
                    ConnectionInjector(
                        bearerTokenHandler = AppBearerTokenHandler,
                        baseUrl = "https://writeopia.io/api",
                        disableWebsocket = true
                    )
                }

            val editorInjector = EditorKmpInjector.mobile(
                sqlDelightDaoInjector,
                connectionInjection,
                uiConfigurationInjector.provideUiConfigurationRepository(),
            )

            val authInjection = AuthInjection(
                connectionInjection,
                sqlDelightDaoInjector
            )
            val navigationViewModel = viewModel { MobileNavigationViewModel() }

            val navController = rememberNavController()

            //Todo: Fix this!
            AppMobile(isDarkTheme = true,
                navController = navController,
                searchInjector = searchInjection,
                uiConfigViewModel = uiConfigurationViewModel,
                notesMenuInjection = notesMenuInjection,
                editorInjector = editorInjector,
                navigationViewModel = navigationViewModel,
            ) {
                authNavigation(navController, authInjection) {
                    navController.navigateToNotes(NotesNavigation.Root)
                }
            }
        }

        DatabaseCreation.Loading -> {
            CircularProgressIndicator()
        }
    }
}
