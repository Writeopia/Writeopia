package io.writeopia.persistence.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.writeopia.persistence.room.data.daos.FolderRoomDao
import io.writeopia.persistence.room.data.daos.NotesConfigurationRoomDao
import io.writeopia.persistence.room.data.entities.FolderEntity
import io.writeopia.persistence.room.data.entities.NotesConfigurationEntity
import io.writeopia.sdk.persistence.converter.IdListConverter
import io.writeopia.sdk.persistence.dao.DocumentEntityDao
import io.writeopia.sdk.persistence.dao.StoryUnitEntityDao
import io.writeopia.sdk.persistence.entity.document.DocumentEntity
import io.writeopia.sdk.persistence.entity.story.StoryStepEntity


@Database(
    entities = [
        DocumentEntity::class,
        StoryStepEntity::class,
        NotesConfigurationEntity::class,
        FolderEntity::class,
    ],
    version = 14,
    exportSchema = false
)
@TypeConverters(IdListConverter::class)
abstract class WriteopiaApplicationDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentEntityDao

    abstract fun storyUnitDao(): StoryUnitEntityDao

    abstract fun notesConfigurationDao(): NotesConfigurationRoomDao

    abstract fun folderRoomDao(): FolderRoomDao

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
