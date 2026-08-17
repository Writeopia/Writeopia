@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.export

import io.writeopia.api.documents.documents.repository.allFoldersByWorkspaceId
import io.writeopia.api.documents.documents.repository.getDocumentIdsByWorkspaceId
import io.writeopia.api.documents.documents.repository.getDocumentWithContentById
import io.writeopia.api.export.model.ExportRequest
import io.writeopia.api.export.model.ExportResult
import io.writeopia.api.export.service.ExportStorageService
import io.writeopia.api.export.service.ZipService
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.data.FolderApi
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ExportJob(
    private val database: WriteopiaDbBackend
) {
    private val logger = LoggerFactory.getLogger(ExportJob::class.java)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun execute(request: ExportRequest): ExportResult {
        logger.info("Starting export for workspace: ${request.workspaceId}, user: ${request.userId}")

        val folders = database.allFoldersByWorkspaceId(request.workspaceId)
        logger.info("Loaded ${folders.size} folders")

        val documentIds = database.getDocumentIdsByWorkspaceId(request.workspaceId)
        logger.info("Found ${documentIds.size} documents to export")

        val folderMap = folders.associateBy { it.id }

        val zipService = ZipService()

        val manifest = ExportManifest(
            workspaceId = request.workspaceId,
            exportedBy = request.userId,
            exportedAt = Clock.System.now().toEpochMilliseconds(),
            documentCount = documentIds.size,
            folderCount = folders.size
        )
        zipService.addEntry("manifest.json", json.encodeToString(manifest))

        folders.forEach { folder ->
            val folderPath = buildFolderPath(folder, folderMap)
            val folderMetadata = folder.toApi()
            zipService.addEntry(
                "$folderPath/.folder.json",
                json.encodeToString(folderMetadata)
            )
        }

        var exportedCount = 0
        documentIds.forEach { documentId ->
            val document = database.getDocumentWithContentById(documentId, request.workspaceId)
            if (document != null) {
                val documentPath = buildDocumentPath(document, folderMap)
                val documentApi = document.toApi()
                zipService.addEntry(
                    documentPath,
                    json.encodeToString(documentApi)
                )
                exportedCount++

                if (exportedCount % 100 == 0) {
                    logger.info("Exported $exportedCount/${documentIds.size} documents")
                }
            } else {
                logger.warn("Document not found: $documentId")
            }
        }

        logger.info("Finished exporting $exportedCount documents")

        val zipBytes = zipService.finish()
        logger.info("Created zip file: ${zipBytes.size} bytes")

        val timestamp = Clock.System.now().toEpochMilliseconds()
        val objectPath = "exports/${request.workspaceId}/$timestamp/workspace-export.zip"
        val bucketName = ExportConfig.exportsBucketName()

        val signedUrl = ExportStorageService.uploadZip(bucketName, objectPath, zipBytes)
        logger.info("Uploaded to GCS: $objectPath")

        return ExportResult(
            signedUrl = signedUrl,
            documentCount = exportedCount,
            folderCount = folders.size,
            exportedAt = timestamp
        )
    }

    private fun buildFolderPath(folder: Folder, folderMap: Map<String, Folder>): String {
        val pathParts = mutableListOf<String>()
        var current: Folder? = folder

        while (current != null && current.parentId.isNotEmpty() && current.parentId != "root") {
            pathParts.add(0, current.id)
            current = folderMap[current.parentId]
        }

        if (current != null) {
            pathParts.add(0, current.id)
        }

        return "folders/" + pathParts.joinToString("/")
    }

    private fun buildDocumentPath(document: Document, folderMap: Map<String, Folder>): String {
        val parentId = document.parentId

        return if (parentId.isEmpty() || parentId == "root" || !folderMap.containsKey(parentId)) {
            "documents/${document.id}.json"
        } else {
            val folder = folderMap[parentId]!!
            val folderPath = buildFolderPath(folder, folderMap)
            "$folderPath/${document.id}.json"
        }
    }
}

@Serializable
private data class ExportManifest(
    val workspaceId: String,
    val exportedBy: String,
    val exportedAt: Long,
    val documentCount: Int,
    val folderCount: Int
)
