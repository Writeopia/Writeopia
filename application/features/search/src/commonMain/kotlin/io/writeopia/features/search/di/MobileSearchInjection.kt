package io.writeopia.features.search.di

import io.writeopia.common.utils.persistence.di.AppDaosInjection
import io.writeopia.core.folders.repository.RoomFolderRepository
import io.writeopia.features.search.ui.SearchKmpViewModel
import io.writeopia.sdk.persistence.core.di.RepositoryInjector

class MobileSearchInjection(
    private val appRoomDaosInjection: AppDaosInjection,
    private val roomInjector: RepositoryInjector,
) : SearchInjection {

    override fun provideViewModel(): SearchKmpViewModel =
        SearchKmpViewModel(
            KmpSearchInjection.singleton().provideRepository(
                folderDao = RoomFolderRepository(appRoomDaosInjection.provideFolderDao()),
                documentDao = roomInjector.provideDocumentRepository()
            )
        )
}
