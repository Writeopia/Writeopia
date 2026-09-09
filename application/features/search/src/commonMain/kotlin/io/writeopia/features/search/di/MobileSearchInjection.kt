package io.writeopia.features.search.di

import io.writeopia.analytics.AnalyticsManager
import io.writeopia.analytics.di.AnalyticsInjection
import io.writeopia.common.utils.persistence.di.AppDaosInjection
import io.writeopia.core.folders.repository.folder.RoomFolderRepository
import io.writeopia.features.search.ui.SearchKmpViewModel
import io.writeopia.sdk.persistence.core.di.RepositoryInjector

class MobileSearchInjection(
    private val appRoomDaosInjection: AppDaosInjection,
    private val roomInjector: RepositoryInjector = RepositoryInjector.singleton(),
    private val analyticsManager: AnalyticsManager =
        AnalyticsInjection.singleton().provideAnalyticsManager(),
) : SearchInjection {

    override fun provideViewModel(): SearchKmpViewModel =
        SearchKmpViewModel(
            searchRepository = KmpSearchInjection.singleton().provideRepository(
                folderDao = RoomFolderRepository(appRoomDaosInjection.provideFolderDao()),
                documentDao = roomInjector.provideDocumentRepository()
            ),
            analyticsManager = analyticsManager,
        )
}
