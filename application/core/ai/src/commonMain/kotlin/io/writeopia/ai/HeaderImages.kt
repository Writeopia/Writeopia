package io.writeopia.ai

import io.writeopia.genai.repository.GenAiRepository
import io.writeopia.sdk.models.utils.ResultData

/**
 * The header pictures of a workspace. Only the cloud draws them, and they are kept on the
 * backend, so any document of the workspace can use them, on any device.
 */
interface HeaderImages {
    /**
     * Draws a header for a document, charged to the user. [instructions] is what the user asks of
     * the picture, empty for none. The result is the URL of a candidate.
     */
    suspend fun generate(
        workspaceId: String,
        documentTitle: String,
        documentText: String,
        instructions: String = ""
    ): ResultData<String>

    /** The URLs of the approved pictures of [workspaceId], the newest first. */
    suspend fun images(workspaceId: String): ResultData<List<String>>

    /** Keeps an approved candidate with the pictures of [workspaceId]. */
    suspend fun save(workspaceId: String, imageUrl: String): ResultData<Unit>

    /** The approved pictures of [workspaceId] with their ids, the newest first, to manage them. */
    suspend fun pictures(workspaceId: String): ResultData<List<WorkspaceHeaderImage>>

    /**
     * Takes the picture [imageId] out of the pictures of [workspaceId]. The documents that already
     * use it as their header keep it.
     */
    suspend fun delete(workspaceId: String, imageId: String): ResultData<Unit>
}

/** An approved header picture of a workspace. */
data class WorkspaceHeaderImage(val id: String, val url: String, val createdAt: Long)

/** The header pictures of the Writeopia backend, drawn with Gemini and served by the CDN. */
class CloudHeaderImages(private val repository: GenAiRepository) : HeaderImages {
    override suspend fun generate(
        workspaceId: String,
        documentTitle: String,
        documentText: String,
        instructions: String
    ): ResultData<String> = repository.generateHeaderImage(workspaceId, documentTitle, documentText, instructions)

    override suspend fun images(workspaceId: String): ResultData<List<String>> =
        repository.headerImages(workspaceId)

    override suspend fun save(workspaceId: String, imageUrl: String): ResultData<Unit> =
        repository.saveHeaderImage(workspaceId, imageUrl)

    override suspend fun pictures(workspaceId: String): ResultData<List<WorkspaceHeaderImage>> =
        when (val result = repository.headerImagesWithIds(workspaceId)) {
            is ResultData.Complete -> ResultData.Complete(
                result.data.map { image -> WorkspaceHeaderImage(image.id, image.url, image.createdAt) }
            )
            is ResultData.Error -> ResultData.Error(result.exception)
            else -> ResultData.Error()
        }

    override suspend fun delete(workspaceId: String, imageId: String): ResultData<Unit> =
        repository.deleteHeaderImage(workspaceId, imageId)
}
