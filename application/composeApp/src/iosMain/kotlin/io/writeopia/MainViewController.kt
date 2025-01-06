package io.writeopia

import androidx.compose.ui.window.ComposeUIViewController
import io.writeopia.notemenu.di.NotesInjector
import io.writeopia.persistence.room.DatabaseConfigIos
import io.writeopia.persistence.room.WriteopiaApplicationDatabase
import io.writeopia.persistence.room.injection.AppRoomDaosInjection

fun MainViewController() = ComposeUIViewController {
    val database = WriteopiaApplicationDatabase.database(DatabaseConfigIos.roomBuilder())

    val appDaosInjection = AppRoomDaosInjection(database)
    val notesInjector = NotesInjector(appDaosInjection)


//    AppMobile()
}
