package io.writeopia.navigation.notes

import androidx.navigation.NavController
import io.writeopia.analytics.WriteopiaEvents
import io.writeopia.analytics.WriteopiaProperties
import io.writeopia.analytics.di.AnalyticsInjection
import io.writeopia.analytics.trackAsync
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.encoding.encodeForNavigation
import io.writeopia.notemenu.data.usecase.NotesNavigationUseCase
import io.writeopia.notemenu.navigation.NAVIGATION_PATH
import io.writeopia.notemenu.navigation.NAVIGATION_TYPE

private fun trackNoteOpened(id: String) {
    AnalyticsInjection.singleton().provideAnalyticsManager().trackAsync(
        WriteopiaEvents.NOTE_OPENED,
        mapOf(WriteopiaProperties.DOCUMENT_ID to id)
    )
}

private fun trackFolderOpened(navigation: NotesNavigation) {
    val folderId = (navigation as? NotesNavigation.Folder)?.id
    AnalyticsInjection.singleton().provideAnalyticsManager().trackAsync(
        WriteopiaEvents.FOLDER_OPENED,
        mapOf(WriteopiaProperties.FOLDER_ID to folderId)
    )
}

fun NavController.navigateToNewNote() {
    val folderId = NotesNavigationUseCase.singleton().navigationState.value.id
    navigate("${Destinations.EDITOR.id}/$folderId")
}

expect fun NavController.navigateToNote(id: String, title: String)

fun NavController.navigateToNoteDesktop(id: String, title: String) {
    val noteId = this.currentBackStackEntry?.savedStateHandle?.get<String?>("noteId")

    if (noteId != id) {
        navigate("${Destinations.EDITOR.id}/$id/${title.encodeForNavigation()}")
        trackNoteOpened(id)
    }
}

fun NavController.navigateToNoteMobile(id: String, title: String) {
    val noteId = this.currentBackStackEntry?.savedStateHandle?.get<String?>("noteId")

    if (noteId != id) {
        navigate("${Destinations.EDITOR.id}/$id/${title.encodeForNavigation()}")
        trackNoteOpened(id)
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
                trackFolderOpened(navigation)
            }
        }

        NotesNavigation.Favorites, NotesNavigation.Root -> {
            val type = this.currentBackStackEntry?.savedStateHandle?.get<String?>(NAVIGATION_TYPE)

            if (type != navigation.navigationType.type) {
                this.navigate(
                    "${Destinations.CHOOSE_NOTE.id}/${navigation.navigationType.type}/path",
                )
                trackFolderOpened(navigation)
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
                trackFolderOpened(navigation)
            }
        }

        NotesNavigation.Favorites, NotesNavigation.Root -> {
            val type = this.currentBackStackEntry?.savedStateHandle?.get<String?>(NAVIGATION_TYPE)

            if (type != navigation.navigationType.type) {
                this.navigate(
                    "${Destinations.CHOOSE_NOTE.id}/${navigation.navigationType.type}/path",
                )
                trackFolderOpened(navigation)
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
            trackFolderOpened(navigation)
        }

        NotesNavigation.Favorites, NotesNavigation.Root -> {
            this.navigate(
                "${Destinations.CHOOSE_NOTE.id}/${navigation.navigationType.type}/path",
            )
            trackFolderOpened(navigation)
        }
    }
}
