package io.writeopia.features.search.di

import io.writeopia.common.utils.persistence.di.AppDaosInjection
import io.writeopia.core.folders.repository.folder.RoomFolderRepository
import io.writeopia.features.search.ui.SearchKmpViewModel
import io.writeopia.sdk.persistence.core.di.RepositoryInjector

class MobileSearchInjection(
    private val appRoomDaosInjection: AppDaosInjection,
    private val roomInjector: RepositoryInjector = RepositoryInjector.singleton(),
) : SearchInjection {

    override fun provideViewModel(): SearchKmpViewModel =
        SearchKmpViewModel(
            KmpSearchInjection.singleton().provideRepository(
                folderDao = RoomFolderRepository(appRoomDaosInjection.provideFolderDao()),
                documentDao = roomInjector.provideDocumentRepository()
            )
        )
}
