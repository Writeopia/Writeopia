package br.com.leandroferreira.app_sample.navigation

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.leandroferreira.app_sample.screens.menu.NotesUseCase
import br.com.leandroferreira.app_sample.screens.menu.ui.screen.ChooseNoteScreen
import br.com.leandroferreira.app_sample.screens.menu.viewmodel.ChooseNoteViewModel
import br.com.leandroferreira.app_sample.screens.note.NoteDetailsScreen
import br.com.leandroferreira.app_sample.screens.note.NoteDetailsViewModel
import br.com.leandroferreira.app_sample.screens.note.NoteDetailsViewModelFactory
import br.com.leandroferreira.app_sample.theme.ApplicationComposeTheme
import com.github.leandroborgesferreira.storyteller.manager.StoryTellerManager
import com.github.leandroborgesferreira.storyteller.persistence.database.StoryTellerDatabase
import com.github.leandroborgesferreira.storyteller.persistence.repository.DocumentRepositoryImpl
import com.github.leandroborgesferreira.storyteller.video.VideoFrameConfig

class NavigationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        VideoFrameConfig.configCoilForVideoFrame(this)

        setContent {
            NavigationGraph()
        }
    }
}

@Composable
fun NavigationGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val database = StoryTellerDatabase.database(context)
    val sharedPreferences = context.getSharedPreferences(
        "br.com.leandroferreira.storyteller.preferences",
        Context.MODE_PRIVATE
    )

    ApplicationComposeTheme {

        NavHost(navController = navController, startDestination = Destinations.CHOOSE_NOTE.id) {
            composable(Destinations.CHOOSE_NOTE.id) {
                val repository = DocumentRepositoryImpl(
                    database.documentDao(),
                    database.storyUnitDao()
                )

                val notesUseCase = NotesUseCase(repository, sharedPreferences)
                val chooseNoteViewModel = ChooseNoteViewModel(notesUseCase)

                ChooseNoteScreen(chooseNoteViewModel = chooseNoteViewModel) { noteId, noteTitle ->
                    navController.navigate(
                        "${Destinations.NOTE_DETAILS.id}/$noteId/$noteTitle"
                    )
                }
            }

            composable(
                route = "${Destinations.NOTE_DETAILS.id}/{noteId}/{noteTitle}",
                arguments = listOf(navArgument("noteId") { type = NavType.StringType })
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getString("noteId")
                val noteTitle = backStackEntry.arguments?.getString("noteTitle")

                if (noteId != null && noteTitle != null) {
                    val repository = DocumentRepositoryImpl(
                        database.documentDao(),
                        database.storyUnitDao()
                    )
                    val storyTellerManager = StoryTellerManager()

                    val noteDetailsViewModel: NoteDetailsViewModel =
                        viewModel(
                            factory = NoteDetailsViewModelFactory(
                                storyTellerManager,
                                repository
                            )
                        )

                    NoteDetailsScreen(
                        noteId.takeIf { it != "null" },
                        noteTitle.takeIf { it != "null" },
                        noteDetailsViewModel
                    )
                } else {
                    throw IllegalArgumentException("Wrong route!")
                }
            }
        }
    }
}

enum class Destinations(val id: String) {
    NOTE_DETAILS("note_details"),
    CHOOSE_NOTE("choose_note"),
}

