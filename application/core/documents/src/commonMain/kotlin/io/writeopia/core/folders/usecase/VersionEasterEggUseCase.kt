@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.usecase

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.tutorials.EasterEggs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Easter egg: creates, in the root of the current workspace, a document about the writer the
 * version of the app is named after.
 */
class VersionEasterEggUseCase(
    private val documentRepository: DocumentRepository,
    private val authRepository: AuthRepository,
) {

    suspend fun create(): Document {
        val workspaceId = (authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()).id
        val now = Clock.System.now()

        val document = writeopiaJson
            .decodeFromString<DocumentApi>(EasterEggs.versionWriterDocument(GenerateId::generate))
            .toModel()
            .copy(
                parentId = Folder.ROOT_PATH,
                workspaceId = workspaceId,
                createdAt = now,
                lastUpdatedAt = now,
            )

        documentRepository.saveDocument(document)
        documentRepository.refreshDocuments()

        return document
    }
}
