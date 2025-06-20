package io.writeopia.persistence.room.injection

import io.writeopia.common.utils.persistence.daos.FolderCommonDao
import io.writeopia.common.utils.persistence.daos.NotesConfigurationCommonDao
import io.writeopia.common.utils.persistence.daos.TokenCommonDao
import io.writeopia.common.utils.persistence.daos.UserCommonDao
import io.writeopia.common.utils.persistence.di.AppDaosInjection
import io.writeopia.persistence.room.WriteopiaApplicationDatabase
import io.writeopia.persistence.room.data.daos.FolderDaoDelegator
import io.writeopia.persistence.room.data.daos.NotesConfigurationRoomDaoDelegator
import io.writeopia.persistence.room.data.daos.TokenDaoDelegator
import io.writeopia.persistence.room.data.daos.UserDao
import io.writeopia.persistence.room.data.daos.UserDaoDelegator

class AppRoomDaosInjection private constructor(
    private val database: WriteopiaApplicationDatabase
) : AppDaosInjection {

    override fun provideConfigurationDao(): NotesConfigurationCommonDao =
        NotesConfigurationRoomDaoDelegator(database.notesConfigurationDao())

    override fun provideFolderDao(): FolderCommonDao = FolderDaoDelegator(database.folderRoomDao())

    override fun provideUserDao(): UserCommonDao = UserDaoDelegator(database.userDao())

    override fun provideTokenDao(): TokenCommonDao = TokenDaoDelegator(database.tokenDao())

    companion object {
        private var instance: AppRoomDaosInjection? = null

        fun singleton(): AppRoomDaosInjection =
            instance ?: AppRoomDaosInjection(WriteopiaRoomInjector.get().database).also {
                instance = it
            }
    }
}
