package io.writeopia.core.folders.di

import io.writeopia.common.utils.persistence.di.AppDaosInjection
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.core.folders.repository.folder.RoomFolderRepository
import io.writeopia.persistence.room.injection.AppRoomDaosInjection

actual class FoldersInjector private constructor(
    private val appRoomDaosInjection: AppDaosInjection
) {
    actual fun provideFoldersRepository(): FolderRepository =
        RoomFolderRepository(appRoomDaosInjection.provideFolderDao())

    actual companion object {
        private var instance: FoldersInjector? = null

        actual fun singleton(): FoldersInjector =
            instance ?: FoldersInjector(AppRoomDaosInjection.singleton()).also {
                instance = it
            }
    }
}
