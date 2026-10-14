@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.request.LinkCardDescriptionRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DocumentsApiTest {

    @Test
    fun `sendDocuments rejects documents from another workspace`() = runTest {
        val api = DocumentsApi(
            client = HttpClient(MockEngine { error("HTTP request should not be sent") }),
            baseUrl = "https://api.example.com"
        )
        val document = Document(
            createdAt = Instant.fromEpochMilliseconds(0),
            lastUpdatedAt = Instant.fromEpochMilliseconds(0),
            lastSyncedAt = null,
            workspaceId = "workspace-a",
            parentId = "root"
        )

        val result = api.sendDocuments(
            documents = listOf(document),
            workspaceId = "workspace-b"
        )

        assertIs<ResultData.Error<Unit>>(result)
    }

    @Test
    fun `describeLinkCard does not call the backend for the disconnected workspace`() = runTest {
        val api = DocumentsApi(
            client = HttpClient(MockEngine { error("HTTP request should not be sent") }),
            baseUrl = "https://api.example.com"
        )
        val card = StoryStep(type = StoryTypes.LINK_CARD.type, url = "https://writeopia.io")

        val result = api.describeLinkCard(
            documentId = "doc",
            workspaceId = Workspace.disconnectedWorkspace().id,
            request = LinkCardDescriptionRequest(storyStep = card.toApi(1.0), position = 1.0, requestTimestamp = 1L)
        )

        assertIs<ResultData.Error<StoryStep>>(result)
    }

    @Test
    fun `document metadata requests report an error instead of throwing when offline`() = runTest {
        // Ktor throws from the engine when there is no network, as in airplane mode.
        val api = DocumentsApi(
            client = HttpClient(MockEngine { throw java.net.UnknownHostException("api.example.com") }),
            baseUrl = "https://api.example.com"
        )

        assertIs<ResultData.Error<Document>>(api.getDocumentById("doc", "workspace"))
        assertIs<ResultData.Error<Boolean>>(api.isDocumentPublished("doc", "workspace"))
    }
}
