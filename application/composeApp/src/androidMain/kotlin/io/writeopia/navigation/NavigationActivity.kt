package io.writeopia.navigation

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.writeopia.AndroidLogger
import io.writeopia.BuildConfig
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.auth.core.token.AppBearerTokenHandler
import io.writeopia.auth.di.AuthInjection
import io.writeopia.auth.navigation.authNavigation
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.di.SharedPreferencesInjector
import io.writeopia.editor.di.EditorKmpInjector
import io.writeopia.features.search.di.KmpSearchInjection
import io.writeopia.features.search.di.MobileSearchInjection
import io.writeopia.mobile.AppMobile
import io.writeopia.notemenu.data.model.NotesNavigation
import io.writeopia.notemenu.di.NotesInjector
import io.writeopia.notemenu.di.NotesMenuKmpInjection
import io.writeopia.notemenu.di.UiConfigurationInjector
import io.writeopia.notemenu.navigation.NoteMenuDestiny
import io.writeopia.notemenu.navigation.navigateToNotes
import io.writeopia.persistence.room.DatabaseConfigAndroid
import io.writeopia.persistence.room.WriteopiaApplicationDatabase
import io.writeopia.persistence.room.injection.AppRoomDaosInjection
import io.writeopia.persistence.room.injection.RoomRepositoryInjection
import io.writeopia.persistence.room.injection.WriteopiaRoomInjector
import io.writeopia.sdk.network.injector.ConnectionInjector
import io.writeopia.ui.image.ImageLoadConfig

class NavigationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val startDestination = if (BuildConfig.DEBUG) {
                NoteMenuDestiny.noteMenu()
            } else {
                Destinations.AUTH_MENU_INNER_NAVIGATION.id
            }

            ImageLoadConfig.configImageLoad()

            NavigationGraph(application = application, startDestination = startDestination)
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun NavigationGraph(
    application: Application,
    navController: NavHostController = rememberNavController(),
    sharedPreferences: SharedPreferences = application.getSharedPreferences(
        "io.writeopia.preferences",
        Context.MODE_PRIVATE
    ),
    database: WriteopiaApplicationDatabase = WriteopiaApplicationDatabase.database(
        DatabaseConfigAndroid.roomBuilder(
            application
        )
    ),
    startDestination: String = Destinations.AUTH_MENU_INNER_NAVIGATION.id
) {
    SharedPreferencesInjector.init(sharedPreferences)
    WriteopiaRoomInjector.init(database)

    val uiConfigInjection = UiConfigurationInjector.singleton()

    val appDaosInjection = AppRoomDaosInjection.singleton()
    val notesInjector = NotesInjector.singleton()
    val connectionInjector = ConnectionInjector(
        bearerTokenHandler = AppBearerTokenHandler,
        baseUrl = BuildConfig.BASE_URL
    )
    val uiConfigViewModel = uiConfigInjection.provideUiConfigurationViewModel()
    val repositoryInjection = RoomRepositoryInjection.singleton()
    val authInjection = AuthInjection(connectionInjector, repositoryInjection)
    val editorInjector = EditorKmpInjector.mobile(
        repositoryInjection,
        connectionInjector,
        uiConfigInjection.provideUiConfigurationRepository(),
        folderInjector = notesInjector,
        configurationInjector = notesInjector
    )
    val notesMenuInjection = NotesMenuKmpInjection.mobile(
        notesInjector,
        repositoryInjection,
    )

    val searchInjector = remember {
        MobileSearchInjection(
            searchInjection = KmpSearchInjection(),
            appRoomDaosInjection = appDaosInjection,
            roomInjector = repositoryInjection
        )
    }

    val navigationViewModel = viewModel { MobileNavigationViewModel() }

    AppMobile(
        startDestination = startDestination,
        navController = navController,
        searchInjector = searchInjector,
        uiConfigViewModel = uiConfigViewModel,
        notesMenuInjection = notesMenuInjection,
        editorInjector = editorInjector,
        navigationViewModel = navigationViewModel
    ) {
        authNavigation(navController, authInjection) {
            navController.navigateToNotes(NotesNavigation.Root)
        }
    }
}
