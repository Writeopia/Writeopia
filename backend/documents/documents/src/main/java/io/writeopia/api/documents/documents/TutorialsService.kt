@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents

import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.sql.WriteopiaDbBackend
import io.writeopia.tutorials.Tutorials
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object TutorialsService {

    private val json: Json = writeopiaJson

    /**
     * Initializes tutorial documents for a user in a workspace.
     *
     * @param userId The user ID
     * @param workspaceId The workspace ID
     * @param writeopiaDb The database instance
     * @return true if tutorials were created, false if they already existed
     */
    suspend fun initializeTutorialsForUser(
        userId: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend,
    ): Boolean {
        // Check if tutorials have already been created for this user/workspace
        val existingStatus = writeopiaDb.workspaceTutorialStatusQueries
            .getTutorialStatus(workspaceId, userId)
            .executeAsOneOrNull()

        if (existingStatus != null && existingStatus > 0) {
            // Tutorials already created
            return false
        }

        val now = Clock.System.now()

        // Create all tutorial documents
        Tutorials.allTutorialsDocuments()
            .map { documentAsJson ->
                json.decodeFromString<DocumentApi>(documentAsJson)
                    .toModel()
            }
            .forEach { document ->
                val documentWithWorkspace = document.copy(
                    workspaceId = workspaceId,
                    createdAt = now,
                    lastUpdatedAt = now,
                    lastSyncedAt = now
                )

                DocumentsService.upsertDocument(
                    document = documentWithWorkspace,
                    workspaceId = workspaceId,
                    writeopiaDb = writeopiaDb,
                    useAi = false
                )
            }

        // Mark tutorials as created
        writeopiaDb.workspaceTutorialStatusQueries.setTutorialStatus(
            workspace_id = workspaceId,
            user_id = userId,
            tutorials_created = 1,
            created_at = now.toEpochMilliseconds()
        )

        return true
    }
}
