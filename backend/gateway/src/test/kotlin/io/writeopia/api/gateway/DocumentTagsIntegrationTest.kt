@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.gateway

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.writeopia.api.documents.documents.repository.deleteDocumentById
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.sdk.models.api.request.documents.FolderDiffRequest
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.data.DocumentTagApi
import io.writeopia.sdk.serialization.json.SendDocumentTagsRequest
import io.writeopia.sdk.serialization.json.SendDocumentsRequest
import io.writeopia.sdk.serialization.request.WorkspaceDiffRequest
import io.writeopia.sdk.serialization.response.FolderContentResponse
import io.writeopia.sdk.serialization.response.WorkspaceDiffResponse
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

class DocumentTagsIntegrationTest {

    private val db = configurePersistence()

    private fun tagApi(id: String, workspaceId: String, name: String, deleted: Boolean = false) =
        DocumentTagApi(
            id = id,
            workspaceId = workspaceId,
            name = name,
            description = "Everything about $name",
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            deleted = deleted,
        )

    @Test
    fun `tags are upserted and come back in the workspace and folder diffs with the documents' ids`() =
        testApplication {
            application { module(db, debugMode = true) }

            val client = defaultClient()
            val workspaceId = "ws-tags-${Random.nextInt()}"
            val folderId = "folder-${Random.nextInt()}"

            val sendTags = client.post("/api/docs/workspace/tag") {
                contentType(ContentType.Application.Json)
                setBody(
                    SendDocumentTagsRequest(
                        tags = listOf(
                            tagApi("tag-a", workspaceId, "Alpha"),
                            tagApi("tag-b", workspaceId, "Beta", deleted = true),
                        ),
                        workspaceId = workspaceId,
                    )
                )
            }
            assertEquals(HttpStatusCode.OK, sendTags.status)

            val documentApi = DocumentApi(
                id = "doc-tags-${Random.nextInt()}",
                title = "Tagged",
                workspaceId = workspaceId,
                parentId = folderId,
                createdAt = 1000L,
                lastUpdatedAt = 2000L,
                tagIds = listOf("tag-a"),
            )
            val sendDocument = client.post("/api/docs/workspace/document") {
                contentType(ContentType.Application.Json)
                setBody(SendDocumentsRequest(listOf(documentApi), workspaceId))
            }
            assertEquals(HttpStatusCode.OK, sendDocument.status)

            val workspaceDiff = client.post("/api/docs/workspace/diff") {
                contentType(ContentType.Application.Json)
                setBody(WorkspaceDiffRequest(workspaceId = workspaceId, lastSync = 0))
            }.body<WorkspaceDiffResponse>()

            assertEquals(listOf("Alpha" to false, "Beta" to true), workspaceDiff.tags.map { it.name to it.deleted })
            assertEquals("Everything about Alpha", workspaceDiff.tags.first().description)
            assertEquals(listOf("tag-a"), workspaceDiff.documents.first { it.id == documentApi.id }.tagIds)

            val folderDiff = client.post("/api/docs/workspace/document/folder/diff") {
                contentType(ContentType.Application.Json)
                setBody(FolderDiffRequest(folderId = folderId, workspaceId = workspaceId, lastFolderSync = 0))
            }.body<FolderContentResponse>()

            assertEquals(listOf("tag-a", "tag-b"), folderDiff.tags.map { it.id })
            assertEquals(listOf("tag-a"), folderDiff.documents.first { it.id == documentApi.id }.tagIds)

            db.deleteDocumentById(documentApi.id)
            db.documentTagLinkEntityQueries.deleteByWorkspace(workspaceId)
            db.documentTagEntityQueries.deleteByWorkspace(workspaceId)
        }
}
