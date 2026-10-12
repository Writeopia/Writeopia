package io.writeopia.writingagent.tags

import io.writeopia.app.dto.writingagent.TagCandidate
import io.writeopia.app.dto.writingagent.TagCreateRequest
import io.writeopia.core.folders.repository.tag.DocumentTagRepository
import io.writeopia.di.ApiLogger
import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.writingagent.api.WritingAgentApi

/**
 * Tags the open document with the app's tag catalogue. A new tag is made up by the cloud AI
 * through the backend, stored in the catalogue, sent to the backend at once when possible, and
 * added to the document.
 */
class WriteopiaDocumentTags(
    private val documentTagIds: () -> Set<String>,
    private val workspaceId: suspend () -> String,
    private val tagRepository: DocumentTagRepository,
    private val api: WritingAgentApi,
    private val addToDocument: (tagId: String) -> Unit,
    /** Sends the new tag to the backend right away; the sync does it later when this fails. */
    private val sendTag: suspend (DocumentTag) -> Unit = {},
    private val log: (String) -> Unit = { message -> ApiLogger.log("[WritingAgent] $message") },
) : DocumentTags {

    override fun currentTagIds(): Set<String> = documentTagIds()

    override suspend fun candidates(limit: Int): List<TagCandidate> =
        tagRepository.getTagsForWorkspace(workspaceId())
            .filter { tag -> tag.name.isNotBlank() && tag.id !in currentTagIds() }
            .take(limit)
            .map { tag -> TagCandidate(id = tag.id, name = tag.name, description = tag.description) }

    override suspend fun addTags(tagIds: Set<String>) {
        tagIds.forEach(addToDocument)
    }

    override suspend fun createTagWithAi(documentTitle: String, documentText: String): Result<DocumentTag> {
        val workspace = workspaceId()
        val existingNames = tagRepository.getTagsForWorkspace(workspace).map { it.name }
        val request = TagCreateRequest(
            documentTitle = documentTitle,
            documentText = documentText,
            existingTagNames = existingNames,
        )

        return when (val result = api.createTag(request)) {
            is ResultData.Complete -> {
                val answer = result.data
                if (answer.error != null || answer.name.isBlank()) {
                    Result.failure(Exception(answer.error ?: "The AI answered no tag name"))
                } else {
                    val tag = DocumentTag.create(answer.name, answer.description, workspace)
                    log("creating tag \"${tag.name}\" (${tag.id}) from the AI")
                    tagRepository.upsertTag(tag)
                    addToDocument(tag.id)
                    runCatching { sendTag(tag) }.onFailure { log("sending the new tag failed: ${it.message}") }
                    Result.success(tag)
                }
            }

            is ResultData.Error -> Result.failure(result.exception ?: Exception("Creating the tag failed"))

            else -> Result.failure(Exception("Creating the tag failed"))
        }
    }
}
