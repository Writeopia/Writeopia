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
import io.writeopia.notemenu.di.NotesMenuKmpInjection
import io.writeopia.notemenu.di.UiConfigurationInjector
import io.writeopia.notemenu.navigation.navigateToNotes
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
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
            RepositoryInjector.initialize(SqlDelightDaoInjector.singleton())

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

            AppMobile(
                startDestination = Destinations.START_APP.id,
                navController = navController,
                searchInjector = searchInjection,
                uiConfigViewModel = uiConfigurationViewModel,
                notesMenuInjection = notesMenuInjection,
                editorInjector = editorInjector,
                navigationViewModel = navigationViewModel,
            ) {
                authNavigation(navController, colorThemeState) {
                    navController.navigateToNotes(NotesNavigation.Root)
                }
            }
        }

        DatabaseCreation.Loading -> {
            CircularProgressIndicator()
        }
    }
}
