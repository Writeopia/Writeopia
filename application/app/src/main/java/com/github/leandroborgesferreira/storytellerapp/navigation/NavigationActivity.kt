package com.github.leandroborgesferreira.storytellerapp.navigation

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.leandroborgesferreira.storyteller.network.injector.ApiInjector
import com.github.leandroborgesferreira.storyteller.persistence.database.StoryTellerDatabase
import com.github.leandroborgesferreira.storyteller.video.VideoFrameConfig
import com.github.leandroborgesferreira.storytellerapp.AndroidLogger
import com.github.leandroborgesferreira.storytellerapp.auth.di.AuthInjection
import com.github.leandroborgesferreira.storytellerapp.auth.navigation.authNavigation
import com.github.leandroborgesferreira.storytellerapp.auth.navigation.navigateToAuthMenu
import com.github.leandroborgesferreira.storytellerapp.auth.token.AmplifyTokenHandler
import com.github.leandroborgesferreira.storytellerapp.note_menu.di.NotesMenuInjection
import com.github.leandroborgesferreira.storytellerapp.editor.di.EditorInjector
import com.github.leandroborgesferreira.storytellerapp.editor.navigation.editorNavigation
import com.github.leandroborgesferreira.storytellerapp.note_menu.navigation.notesMenuNavigation
import com.github.leandroborgesferreira.storytellerapp.theme.ApplicationComposeTheme
import com.github.leandroborgesferreira.storytellerapp.utils_module.Destinations

class NavigationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        VideoFrameConfig.configCoilForVideoFrame(this)

        setContent {
            NavigationGraph(application = application)
        }
    }
}

@Composable
fun NavigationGraph(
    application: Application,
    navController: NavHostController = rememberNavController(),
    database: StoryTellerDatabase = StoryTellerDatabase.database(application),
    sharedPreferences: SharedPreferences = application.getSharedPreferences(
        "com.github.leandroborgesferreira.storytellerapp.preferences",
        Context.MODE_PRIVATE
    ),
) {

    val apiInjector =
        ApiInjector(apiLogger = AndroidLogger, bearerTokenHandler = AmplifyTokenHandler)
    val editorInjector = EditorInjector(database)
    val notesMenuInjection = NotesMenuInjection(database, sharedPreferences)
    val authInjection = AuthInjection(database, apiInjector = apiInjector)

    val startDestination = Destinations.CHOOSE_NOTE.id

    ApplicationComposeTheme {
        NavHost(navController = navController, startDestination = startDestination) {
            authNavigation(navController, authInjection, navController::navigateToMainMenu)

            notesMenuNavigation(
                notesMenuInjection = notesMenuInjection,
                navigateToNote = navController::navigateToNote,
                navigateToAuthMenu = navController::navigateToAuthMenu,
                navigateToNewNote = navController::navigateToNewNote
            )

            editorNavigation(
                editorInjector = editorInjector,
                navigateToNoteMenu = navController::navigateToNoteMenu
            )
        }
    }
}
