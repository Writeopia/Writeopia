package io.writeopia.persistence.room.injection

import io.writeopia.persistence.room.WriteopiaApplicationDatabase
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.repository.PdfDocumentRepository
import io.writeopia.sdk.persistence.dao.room.RoomDocumentRepository
import io.writeopia.sdk.persistence.dao.room.RoomPdfDocumentRepository

class RoomRepositoryInjection private constructor(
    private val database: WriteopiaApplicationDatabase
) : RepositoryInjector {

    override fun provideDocumentRepository(): DocumentRepository =
        RoomDocumentRepository(
            database.documentDao(),
            database.storyUnitDao()
        )

    override fun providePdfDocumentRepository(): PdfDocumentRepository =
        RoomPdfDocumentRepository(
            database.pdfDocumentDao()
        )

    companion object {
        private var instance: RoomRepositoryInjection? = null

        fun singleton() = instance ?: RoomRepositoryInjection(
            WriteopiaRoomInjector.get().database
        ).also {
            instance = it
        }
    }

}
