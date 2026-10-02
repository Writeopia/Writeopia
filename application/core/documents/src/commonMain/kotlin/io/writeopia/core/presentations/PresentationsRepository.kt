package io.writeopia.core.presentations

import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.models.utils.ResultData

/**
 * Where the presentations of the documents are read from: the backend in an online workspace,
 * the device with the local AI.
 */
interface PresentationsRepository {
    /** The presentations of a document with their slides, newest first. */
    suspend fun presentations(documentId: String, workspaceId: String): ResultData<List<Presentation>>

    suspend fun presentation(id: String, workspaceId: String): ResultData<Presentation?>

    suspend fun deletePresentation(id: String, workspaceId: String): ResultData<Unit>
}

/** A repository on the device, which also keeps the presentations the local AI generates. */
interface PresentationsStore : PresentationsRepository {
    suspend fun savePresentation(presentation: Presentation): ResultData<Unit>
}

/**
 * Makes a presentation of a document. The cloud does everything on the server; the local
 * generator asks the model on this machine and parses its answer here.
 */
interface PresentationGenerator {
    /** The new presentation, already saved where the [PresentationsRepository] reads it. */
    suspend fun generatePresentation(documentId: String, workspaceId: String): ResultData<Presentation>
}

/** What the AI answered that can't be turned into slides, or what the backend refused. */
class PresentationException(message: String) : Exception(message)
