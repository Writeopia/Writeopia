package io.writeopia.core.folders.di

import io.writeopia.common.utils.persistence.di.AppDaosInjection
import io.writeopia.core.folders.repository.tag.DaoDocumentTagRepository
import io.writeopia.core.folders.repository.tag.DocumentTagRepository
import io.writeopia.persistence.room.injection.AppRoomDaosInjection

actual class DocumentTagsInjector private constructor(
    private val appRoomDaosInjection: AppDaosInjection
) {
    private val repository by lazy {
        DaoDocumentTagRepository(appRoomDaosInjection.provideDocumentTagDao())
    }

    actual fun provideDocumentTagRepository(): DocumentTagRepository = repository

    actual companion object {
        private var instance: DocumentTagsInjector? = null

        actual fun singleton(): DocumentTagsInjector =
            instance ?: DocumentTagsInjector(AppRoomDaosInjection.singleton()).also {
                instance = it
            }
    }
}
