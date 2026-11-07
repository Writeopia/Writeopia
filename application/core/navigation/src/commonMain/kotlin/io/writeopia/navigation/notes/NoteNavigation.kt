package io.writeopia.navigation.notes

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.encoding.encodeForNavigation
import io.writeopia.notemenu.data.usecase.NotesNavigationUseCase
import io.writeopia.notemenu.navigation.NAVIGATION_PATH
import io.writeopia.notemenu.navigation.NAVIGATION_TYPE

fun NavController.navigateToNewNote() {
    val folderId = NotesNavigationUseCase.singleton().navigationState.value.id
    navigate("${Destinations.EDITOR.id}/$folderId")
}

expect fun NavController.navigateToNote(id: String, title: String)

fun NavController.navigateToNoteDesktop(id: String, title: String) {
    val noteId = this.currentBackStackEntry?.savedStateHandle?.get<String?>("noteId")

    if (noteId != id) {
        navigate("${Destinations.EDITOR.id}/$id/${title.encodeForNavigation()}")
    }
}

fun NavController.navigateToNoteMobile(id: String, title: String) {
    val noteId = this.currentBackStackEntry?.savedStateHandle?.get<String?>("noteId")

    if (noteId != id) {
        navigate("${Destinations.EDITOR.id}/$id/${title.encodeForNavigation()}")
    }
}

/**
 * Opens the document a presentation was made from and plays the presentation over it. Navigates
 * even when the document is already open, so the presentation shows.
 */
fun NavController.navigateToNoteWithPresentation(documentId: String, presentationId: String, title: String) {
    navigate("${Destinations.EDITOR.id}/$documentId/${title.encodeForNavigation()}?presentationId=$presentationId")
}

/**
 * Opens a document found by the words inside it, with the in-document search active on [query]:
 * the matches highlighted and the first one scrolled to. Navigates even when the document is
 * already open, so the search applies.
 */
fun NavController.navigateToNoteWithSearch(documentId: String, title: String, query: String) {
    navigate(
        "${Destinations.EDITOR.id}/$documentId/${title.encodeForNavigation()}?searchQuery=${query.encodeForNavigation()}"
    )
}

fun NavController.navigateToAccount(builder: NavOptionsBuilder.() -> Unit = {}) {
    navigate(Destinations.ACCOUNT.id, builder)
}

fun NavController.navigateToFolder(navigation: NotesNavigation) {
    when (navigation) {
        is NotesNavigation.Folder, is NotesNavigation.Tag -> {
            val id = this.currentBackStackEntry?.savedStateHandle?.get<String?>(NAVIGATION_PATH)

            if (id != navigation.id) {
                this.navigate(
                    "${Destinations.CHOOSE_NOTE.id}/${navigation.navigationType.type}/${navigation.id}",
                )
            }
        }

        NotesNavigation.Favorites, NotesNavigation.Root -> {
            val type = this.currentBackStackEntry?.savedStateHandle?.get<String?>(NAVIGATION_TYPE)

            if (type != navigation.navigationType.type) {
                this.navigate(
                    "${Destinations.CHOOSE_NOTE.id}/${navigation.navigationType.type}/path",
                )
            }
        }
    }
}

fun NavController.navigateToFolderDesktop(navigation: NotesNavigation) {
    when (navigation) {
        is NotesNavigation.Folder, is NotesNavigation.Tag -> {
            val id = this.currentBackStackEntry?.savedStateHandle?.get<String?>(NAVIGATION_PATH)

            if (id != navigation.id) {
                this.navigate(
                    "${Destinations.CHOOSE_NOTE.id}/${navigation.navigationType.type}/${navigation.id}",
                )
            }
        }

        NotesNavigation.Favorites, NotesNavigation.Root -> {
            val type = this.currentBackStackEntry?.savedStateHandle?.get<String?>(NAVIGATION_TYPE)

            if (type != navigation.navigationType.type) {
                this.navigate(
                    "${Destinations.CHOOSE_NOTE.id}/${navigation.navigationType.type}/path",
                )
            }
        }
    }
}

fun NavController.navigateToFolderMobile(navigation: NotesNavigation) {
    when (navigation) {
        is NotesNavigation.Folder, is NotesNavigation.Tag -> {
            this.navigate(
                "${Destinations.CHOOSE_NOTE.id}/${navigation.navigationType.type}/${navigation.id}",
            )
        }

        NotesNavigation.Favorites, NotesNavigation.Root -> {
            this.navigate(
                "${Destinations.CHOOSE_NOTE.id}/${navigation.navigationType.type}/path",
            )
        }
    }
}
