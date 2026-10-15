package io.writeopia.ai

import io.writeopia.genai.repository.GenAiRepository
import io.writeopia.sdk.models.utils.ResultData

/**
 * The header pictures of a workspace. Only the cloud draws them, and they are kept on the
 * backend, so any document of the workspace can use them, on any device.
 */
interface HeaderImages {
    /** Draws a header for a document, charged to the user. The result is the URL of a candidate. */
    suspend fun generate(workspaceId: String, documentTitle: String, documentText: String): ResultData<String>

    /** The URLs of the approved pictures of [workspaceId], the newest first. */
    suspend fun images(workspaceId: String): ResultData<List<String>>

    /** Keeps an approved candidate with the pictures of [workspaceId]. */
    suspend fun save(workspaceId: String, imageUrl: String): ResultData<Unit>
}

/** The header pictures of the Writeopia backend, drawn with Gemini and served by the CDN. */
class CloudHeaderImages(private val repository: GenAiRepository) : HeaderImages {
    override suspend fun generate(
        workspaceId: String,
        documentTitle: String,
        documentText: String
    ): ResultData<String> = repository.generateHeaderImage(workspaceId, documentTitle, documentText)

    override suspend fun images(workspaceId: String): ResultData<List<String>> =
        repository.headerImages(workspaceId)

    override suspend fun save(workspaceId: String, imageUrl: String): ResultData<Unit> =
        repository.saveHeaderImage(workspaceId, imageUrl)
}
