package io.writeopia.editor.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.writeopia.common.utils.Destinations
import io.writeopia.editor.di.TextEditorInjector
import io.writeopia.editor.features.editor.copy.CopyManager
import io.writeopia.editor.features.editor.ui.screen.TextEditorScreen
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.editor.features.presentation.ui.PresentationScreen
import io.writeopia.theme.WriteopiaTheme

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.editorNavigation(
    isDarkTheme: Boolean,
    navigateBack: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
    editorInjector: TextEditorInjector,
    navigateToNote: (String) -> Unit,
    navigateToPresentation: (String) -> Unit
) {
    sharedTransitionScope.run {
        composable(
            route = "${Destinations.EDITOR.id}/{noteId}/{noteTitle}",
            arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { intSize -> intSize }
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { intSize -> intSize }
                )
            }
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId")
            val noteTitle = backStackEntry.arguments?.getString("noteTitle")
            val parentFolderId = backStackEntry.arguments?.getString("parentFolderId")

            if (noteId != null && noteTitle != null) {
                val noteDetailsViewModel =
                    editorInjector.provideNoteDetailsViewModel(
                        parentFolderId ?: "root",
                        copyManager = CopyManager(LocalClipboardManager.current)
                    )

                noteDetailsViewModel.setTheme(isDarkTheme)

                TextEditorScreen(
                    noteId.takeIf { it != "null" },
                    noteTitle.takeIf { it != "null" },
                    isDarkTheme,
                    noteDetailsViewModel,
                    playPresentation = {
                        navigateToPresentation(noteId)
                    },
                    navigateBack = navigateBack,
                    onDocumentLinkClick = navigateToNote,
                    modifier = sharedModifier(this, noteId)
                )
            } else {
                throw IllegalArgumentException("The arguments for this route are wrong!")
            }
        }

        composable(route = "${Destinations.EDITOR.id}/{parentFolderId}") { backStackEntry ->
            val parentFolderId = backStackEntry.arguments?.getString("parentFolderId")
            val notesDetailsViewModel: NoteEditorViewModel =
                editorInjector.provideNoteDetailsViewModel(
                    parentFolderId ?: "root",
                    copyManager = CopyManager(LocalClipboardManager.current)
                ).apply {
                    setTheme(isDarkTheme)
                }

            TextEditorScreen(
                documentId = null,
                title = null,
                isDarkTheme = isDarkTheme,
                noteEditorViewModel = notesDetailsViewModel,
                navigateBack = navigateBack,
                playPresentation = {
                    notesDetailsViewModel.writeopiaManager
                        .documentInfo
                        .value
                        .id
                        .let(navigateToPresentation)
                },
                onDocumentLinkClick = navigateToNote,
                modifier = sharedModifier(this),
            )
        }

        composable(route = "${Destinations.PRESENTATION.id}/{documentId}") { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId")
            val viewModel = editorInjector.providePresentationViewModel()

            if (documentId != null) {
                viewModel.loadDocument(documentId)
            }

            PresentationScreen(viewModel)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.sharedModifier(
    animatedVisibilityScope: AnimatedVisibilityScope,
    documentId: String? = null
) =
    Modifier
        .sharedBounds(
            rememberSharedContentState(key = "noteInit${documentId ?: ""}"),
            animatedVisibilityScope = animatedVisibilityScope,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
        )
        .background(WriteopiaTheme.colorScheme.cardBg, MaterialTheme.shapes.large)
        .clip(MaterialTheme.shapes.large)
