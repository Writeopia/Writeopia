package io.writeopia.navigation

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.writeopia.BuildConfig
import io.writeopia.auth.di.AuthInjection
import io.writeopia.auth.navigation.authNavigation
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.di.SharedPreferencesInjector
import io.writeopia.editor.di.EditorKmpInjector
import io.writeopia.features.search.di.MobileSearchInjection
import io.writeopia.mobile.AppMobile
import io.writeopia.notemenu.di.NotesMenuKmpInjection
import io.writeopia.notemenu.di.UiConfigurationInjector
import io.writeopia.notemenu.navigation.NoteMenuDestiny
import io.writeopia.notemenu.navigation.navigateToNotes
import io.writeopia.persistence.room.DatabaseConfigAndroid
import io.writeopia.persistence.room.WriteopiaApplicationDatabase
import io.writeopia.persistence.room.injection.AppRoomDaosInjection
import io.writeopia.persistence.room.injection.RoomRepositoryInjection
import io.writeopia.persistence.room.injection.WriteopiaRoomInjector
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.ui.image.ImageLoadConfig

class NavigationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ImageLoadConfig.configImageLoad()

            NavigationGraph(
                application = application,
                startDestination = NoteMenuDestiny.noteMenu()
            )
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
    startDestination: String
) {
    SharedPreferencesInjector.init(sharedPreferences)
    WriteopiaRoomInjector.init(database)

    val uiConfigInjection = UiConfigurationInjector.singleton()

    val appDaosInjection = AppRoomDaosInjection.singleton()
    WriteopiaConnectionInjector.setBaseUrl(BuildConfig.BASE_URL)
    val uiConfigViewModel = uiConfigInjection.provideUiConfigurationViewModel()
    val repositoryInjection = RoomRepositoryInjection.singleton()
    val authInjection = AuthInjection()
    val editorInjector = EditorKmpInjector.mobile(repositoryInjection)
    val notesMenuInjection = NotesMenuKmpInjection.mobile(repositoryInjection)

    val searchInjector = remember {
        MobileSearchInjection(
            appRoomDaosInjection = appDaosInjection,
            roomInjector = repositoryInjection
        )
    }

    val navigationViewModel = viewModel { MobileNavigationViewModel() }
    val colorThemeState = uiConfigViewModel.listenForColorTheme { "disconnected_user" }

    AppMobile(
        startDestination = startDestination,
        navController = navController,
        searchInjector = searchInjector,
        uiConfigViewModel = uiConfigViewModel,
        notesMenuInjection = notesMenuInjection,
        editorInjector = editorInjector,
        navigationViewModel = navigationViewModel
    ) {
        authNavigation(navController, authInjection, colorThemeState) {
            navController.navigateToNotes(NotesNavigation.Root)
        }
    }
}
