package io.writeopia

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import io.writeopia.editor.di.EditorKmpInjector
import io.writeopia.features.search.di.KmpSearchInjection
import io.writeopia.mobile.AppMobile
import io.writeopia.navigation.MobileNavigationViewModel
import io.writeopia.notemenu.di.NotesMenuKmpInjection
import io.writeopia.notemenu.di.UiConfigurationInjector
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
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
            WriteopiaConnectionInjector.setBaseUrl(
                "https://writeopia.dev"
//                        "http://localhost:8080"
            )

            val searchInjection = remember { KmpSearchInjection.singleton() }

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
                navigationViewModel,
                editorInjector,
                notesMenuInjection,
                searchInjection,
                uiConfigViewModel,
                colorThemeState,
                navController
            )
        }

        DatabaseCreation.Loading -> {
            Box {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
