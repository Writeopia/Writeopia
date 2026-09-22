@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.api

import io.ktor.client.HttpClient
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.utils.ResultData
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DocumentsApiTest {

    @Test
    fun `sendDocuments rejects documents from another workspace`() = runTest {
        val api = DocumentsApi(
            client = mockk<HttpClient>(relaxed = true),
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
}
