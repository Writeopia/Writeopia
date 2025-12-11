package io.writeopia.navigation.notes

import androidx.navigation.NavController
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.NotesNavigation
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
        navigate("${Destinations.EDITOR.id}/$id/$title")
    }
}

fun NavController.navigateToNoteMobile(id: String, title: String) {
    val noteId = this.currentBackStackEntry?.savedStateHandle?.get<String?>("noteId")

    if (noteId != id) {
        navigate("${Destinations.EDITOR.id}/$id/$title")
    }
}

fun NavController.navigateToAccount() {
    navigate(Destinations.ACCOUNT.id)
}

fun NavController.navigateToFolder(navigation: NotesNavigation) {
    when (navigation) {
        is NotesNavigation.Folder -> {
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
        is NotesNavigation.Folder -> {
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
        is NotesNavigation.Folder -> {
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
