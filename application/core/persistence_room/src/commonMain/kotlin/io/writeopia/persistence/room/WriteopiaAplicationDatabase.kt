package io.writeopia.persistence.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import io.writeopia.persistence.room.data.daos.FolderRoomDao
import io.writeopia.persistence.room.data.daos.NotesConfigurationRoomDao
import io.writeopia.persistence.room.data.daos.TokenDao
import io.writeopia.persistence.room.data.daos.UiConfigurationRoomDao
import io.writeopia.persistence.room.data.daos.UserDao
import io.writeopia.persistence.room.data.daos.WorkspaceDao
import io.writeopia.persistence.room.data.entities.FolderEntity
import io.writeopia.persistence.room.data.entities.NotesConfigurationEntity
import io.writeopia.persistence.room.data.entities.TokenEntity
import io.writeopia.persistence.room.data.entities.UiConfigurationRoomEntity
import io.writeopia.persistence.room.data.entities.UserEntity
import io.writeopia.persistence.room.data.entities.WorkspaceEntity
import io.writeopia.sdk.persistence.converter.IdListConverter
import io.writeopia.sdk.persistence.dao.DocumentEntityDao
import io.writeopia.sdk.persistence.dao.StoryUnitEntityDao
import io.writeopia.sdk.persistence.entity.document.DocumentEntity
import io.writeopia.sdk.persistence.entity.story.StoryStepEntity

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<WriteopiaApplicationDatabase> {
    override fun initialize(): WriteopiaApplicationDatabase
}

@Database(
    entities = [
        DocumentEntity::class,
        StoryStepEntity::class,
        NotesConfigurationEntity::class,
        FolderEntity::class,
        UiConfigurationRoomEntity::class,
        UserEntity::class,
        TokenEntity::class,
        WorkspaceEntity::class
    ],
    version = 24,
    exportSchema = false
)
@TypeConverters(IdListConverter::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class WriteopiaApplicationDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentEntityDao

    abstract fun storyUnitDao(): StoryUnitEntityDao

    abstract fun notesConfigurationDao(): NotesConfigurationRoomDao

    abstract fun folderRoomDao(): FolderRoomDao

    abstract fun userDao(): UserDao

    abstract fun uiConfigDao(): UiConfigurationRoomDao

    abstract fun tokenDao(): TokenDao

    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        private var instance: WriteopiaApplicationDatabase? = null

        fun database(
            databaseBuilder: Builder<WriteopiaApplicationDatabase>
        ): WriteopiaApplicationDatabase =
            instance ?: createDatabase(databaseBuilder)

        private fun createDatabase(
            databaseBuilder: Builder<WriteopiaApplicationDatabase>,
        ): WriteopiaApplicationDatabase =
            databaseBuilder
//                    .createFromAsset("WriteopiaDatabase.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { database ->
                    instance = database
                }
    }
}
