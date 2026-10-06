package io.writeopia.editor.features.presentation.repository

import io.writeopia.core.presentations.PresentationGenerator
import io.writeopia.core.presentations.PresentationsRepository
import io.writeopia.core.presentations.PresentationsStore
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.models.utils.ResultData

/**
 * The presentations of the cloud, saved on the device too: every presentation the backend sends
 * (generated, listed or opened) is saved in [store] (the app database), so the search finds it
 * and it opens offline. When the backend can't be reached, the presentations are read from [store].
 */
class CloudPresentationsSaver<T>(
    private val remote: T,
    private val store: PresentationsStore
) : PresentationsRepository, PresentationGenerator where T : PresentationsRepository, T : PresentationGenerator {

    override suspend fun presentations(documentId: String, workspaceId: String): ResultData<List<Presentation>> =
        when (val result = remote.presentations(documentId, workspaceId)) {
            is ResultData.Complete -> {
                result.data.forEach { store.savePresentation(it) }
                result
            }

            is ResultData.Error -> store.presentations(documentId, workspaceId).orElse(result)

            else -> result
        }

    override suspend fun presentation(id: String, workspaceId: String): ResultData<Presentation?> =
        when (val result = remote.presentation(id, workspaceId)) {
            is ResultData.Complete -> {
                result.data?.let { store.savePresentation(it) }
                result
            }

            is ResultData.Error -> store.presentation(id, workspaceId).orElse(result)

            else -> result
        }

    override suspend fun deletePresentation(id: String, workspaceId: String): ResultData<Unit> {
        val result = remote.deletePresentation(id, workspaceId)
        if (result is ResultData.Complete) store.deletePresentation(id, workspaceId)
        return result
    }

    override suspend fun generatePresentation(documentId: String, workspaceId: String): ResultData<Presentation> {
        val result = remote.generatePresentation(documentId, workspaceId)
        if (result is ResultData.Complete) store.savePresentation(result.data)
        return result
    }

    /** What [store] has, or the error of the backend when [store] has nothing either. */
    private fun <R> ResultData<R>.orElse(error: ResultData.Error<*>): ResultData<R> =
        when {
            this is ResultData.Complete && data != null && (data as? List<*>)?.isEmpty() != true -> this
            else -> ResultData.Error(error.exception)
        }
}
