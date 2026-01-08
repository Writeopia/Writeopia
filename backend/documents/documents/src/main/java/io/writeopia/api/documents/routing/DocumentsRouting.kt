@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.writeopia.api.core.auth.routing.getUserId
import io.writeopia.api.core.auth.utils.runIfMember
import io.writeopia.api.documents.documents.DocumentsService
import io.writeopia.api.documents.documents.repository.allFoldersByWorkspaceId
import io.writeopia.api.documents.documents.repository.documentsDiffByFolder
import io.writeopia.api.documents.documents.repository.documentsDiffByWorkspace
import io.writeopia.api.documents.documents.repository.getDocumentsByParentId
import io.writeopia.api.documents.documents.repository.getIdsByParentId
import io.writeopia.backend.models.ImageStorageService
import io.writeopia.buckets.GcpBucketImageStorageService
import io.writeopia.connection.ResultData
import io.writeopia.connection.map
import io.writeopia.sdk.models.api.request.documents.FolderDiffRequest
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.json.SendDocumentsRequest
import io.writeopia.sdk.serialization.json.SendFoldersRequest
import io.writeopia.sdk.serialization.request.ImageUploadRequest
import io.writeopia.sdk.serialization.request.WorkspaceDiffRequest
import io.writeopia.sdk.serialization.response.WorkspaceDiffResponse
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

//Todo: Add a check that only users or a workspace are allowed to interact with this endpoints.
// They can only access the resources of the workspace
fun Routing.documentsRoute(
    writeopiaDb: WriteopiaDbBackend,
    useAi: Boolean,
    debug: Boolean = false,
    imageStorageService: ImageStorageService = GcpBucketImageStorageService
) {
    authenticate("auth-jwt", optional = debug) {
        get("/api/workspace/{workspaceId}/document/{id}") {
            val userId = getUserId() ?: ""
            val id = call.pathParameters["id"] ?: ""
            val workspaceId = call.pathParameters["workspaceId"] ?: ""

            runIfMember(userId, workspaceId, writeopiaDb, debug) {
                val document = DocumentsService.getDocumentById(id, workspaceId, writeopiaDb)

                if (document != null) {
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = document.toApi()
                    )
                } else {
                    call.respond(
                        status = HttpStatusCode.NotFound,
                        message = "No lead with id: $id"
                    )
                }
            }
        }
    }

    authenticate("auth-jwt", optional = debug) {
        get("/api/workspace/{workspaceId}/document/parent/{parentId}") {
            val userId = getUserId() ?: ""
            val workspaceId = call.pathParameters["workspaceId"] ?: ""

            runIfMember(userId, workspaceId, writeopiaDb, debug) {
                val parentId = call.pathParameters["parentId"]!!
                val documentList = writeopiaDb.getDocumentsByParentId(parentId)

                if (documentList.isNotEmpty()) {
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = documentList.map { it.toApi() }
                    )
                } else {
                    call.respond(
                        status = HttpStatusCode.NotFound,
                        message = "No lead with id parent parentId: $parentId"
                    )
                }
            }
        }
    }

    authenticate("auth-jwt", optional = debug) {
        get("/api/workspace/{workspaceId}/document/parent/{id}") {
            val userId = getUserId() ?: ""
            val workspaceId = call.pathParameters["workspaceId"] ?: ""
            val id = call.pathParameters["id"]!!

            runIfMember(userId, workspaceId, writeopiaDb, debug) {
                val ids = writeopiaDb.getIdsByParentId(id)

                if (ids.isNotEmpty()) {
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = ids
                    )
                } else {
                    call.respond(
                        status = HttpStatusCode.NotFound,
                        message = "document id by parent with id: $id"
                    )
                }
            }
        }
    }

    authenticate("auth-jwt", optional = debug) {
        get("/api/workspace/{workspaceId}/document/search") {
            val userId = getUserId() ?: ""
            val query = call.queryParameters["q"]
            val workspaceId = call.pathParameters["workspaceId"] ?: ""

            runIfMember(userId, workspaceId, writeopiaDb, debug) {
                if (query == null) {
                    call.respond(HttpStatusCode.BadRequest)
                } else {
                    val result =
                        DocumentsService.search(query, userId, writeopiaDb).map { resultData ->
                            resultData.map { document -> document.toApi() }
                        }

                    if (result is ResultData.Complete) {
                        call.respond(status = HttpStatusCode.OK, message = result.data)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }
            }
        }
    }

    authenticate("auth-jwt", optional = debug) {
        get("/api/workspace/{workspaceId}/folder/{id}") {
            val id = call.pathParameters["id"]!!
            val userId = getUserId() ?: ""
            val workspaceId = call.pathParameters["workspaceId"] ?: ""

            runIfMember(userId, workspaceId, writeopiaDb, debug) {
                val folder = DocumentsService.getFolderById(id, userId, writeopiaDb)

                if (folder != null) {
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = folder.toApi()
                    )
                } else {
                    call.respond(
                        status = HttpStatusCode.NotFound,
                        message = "No lead with id: $id"
                    )
                }
            }
        }
    }

    authenticate("auth-jwt", optional = debug) {
        post<SendDocumentsRequest>("/api/workspace/document") { request ->
            val userId = getUserId() ?: ""
            val workspaceId = request.workspaceId
            val documentList = request.documents.map { document ->
                document.copy(workspaceId = workspaceId)
            }

            runIfMember(userId, workspaceId, writeopiaDb, debug) {
                try {
                    if (documentList.isNotEmpty()) {
                        val addedToHub = DocumentsService.receiveDocuments(
                            documentList.map { document ->
                                document
                                    .toModel()
                                    .copy(lastSyncedAt = Clock.System.now())
                            },
                            workspaceId = workspaceId,
                            writeopiaDb,
                            useAi
                        )

                        if (addedToHub) {
                            call.respond(
                                status = HttpStatusCode.OK,
                                message = "Accepted"
                            )
                        } else {
                            call.respond(
                                status = HttpStatusCode.InternalServerError,
                                message = "It was not possible to add documents to AI HUB"
                            )
                        }
                    } else {
                        call.respond(
                            status = HttpStatusCode.OK,
                            message = "Empty documents"
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        status = HttpStatusCode.InternalServerError,
                        message = "${e.message}"
                    )
                }
            }
        }
    }

    authenticate("auth-jwt", optional = debug) {
        post<SendFoldersRequest>("/api/workspace/folder") { request ->
            val userId = getUserId() ?: ""
            val workspaceId = request.workspaceId

            runIfMember(userId, workspaceId, writeopiaDb, debug) {
                try {
                    val folderList = request.folders.map { folder ->
                        folder.copy(workspaceId = workspaceId)
                    }

                    if (folderList.isNotEmpty()) {
                        val addedToHub = DocumentsService.receiveFolders(
                            folderList.map { folder -> folder.toModel() },
                            writeopiaDb,
                        )

                        if (addedToHub) {
                            call.respond(
                                status = HttpStatusCode.OK,
                                message = "Accepted"
                            )
                        } else {
                            call.respond(
                                status = HttpStatusCode.InternalServerError,
                                message = "It was not possible to add documents to AI HUB"
                            )
                        }
                    } else {
                        call.respond(
                            status = HttpStatusCode.OK,
                            message = "Empty documents"
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(
                        status = HttpStatusCode.InternalServerError,
                        message = "${e.message}"
                    )
                }
            }
        }
    }

    authenticate("auth-jwt", optional = debug) {
        post<FolderDiffRequest>("/api/workspace/document/folder/diff") { folderDiff ->
            val userId = getUserId() ?: ""
            val workspaceId = folderDiff.workspaceId

            runIfMember(userId, workspaceId, writeopiaDb, debug) {
                try {
                    println("loading folder diff")
                    println("user id: ${getUserId()}")
                    println("last sync: ${Instant.fromEpochMilliseconds(folderDiff.lastFolderSync)}")

                    val documents =
                        writeopiaDb.documentsDiffByFolder(
                            folderDiff.folderId,
                            folderDiff.workspaceId,
                            folderDiff.lastFolderSync
                        )

                    println("returning ${documents.count()} documents")

                    call.respond(
                        status = HttpStatusCode.OK,
                        message = documents.map { document -> document.toApi() }
                    )
                } catch (e: Exception) {
                    call.respond(
                        status = HttpStatusCode.InternalServerError,
                        message = "${e.message}"
                    )
                }
            }
        }
    }

    authenticate("auth-jwt", optional = debug) {
        post<WorkspaceDiffRequest>("/api/workspace/diff") { workspaceDiff ->
            val userId = getUserId() ?: ""
            val workspaceId = workspaceDiff.workspaceId

            runIfMember(userId, workspaceId, writeopiaDb, debug) {
                try {
                    println("loading workspace diff")
                    println("user id: ${getUserId()}")
                    println("last sync: ${Instant.fromEpochMilliseconds(workspaceDiff.lastSync)}")

                    val documents = writeopiaDb.documentsDiffByWorkspace(
                        workspaceDiff.workspaceId,
                        workspaceDiff.lastSync
                    )
                    val folders = writeopiaDb.allFoldersByWorkspaceId(workspaceDiff.workspaceId)

                    println("returning ${documents.count()} documents and ${folders.count()} folders")

                    call.respond(
                        status = HttpStatusCode.OK,
                        message = WorkspaceDiffResponse(
                            folders.map { it.toApi() },
                            documents.map { it.toApi() }
                        )
                    )
                } catch (e: Exception) {
                    call.respond(
                        status = HttpStatusCode.InternalServerError,
                        message = "${e.message}"
                    )
                }
            }
        }
    }

    authenticate("auth-jwt", optional = debug) {
        post("/api/workspace/{workspaceId}/document/upload-image") {
            val userId = getUserId() ?: ""
            val workspaceId = call.pathParameters["workspaceId"] ?: ""

            runIfMember(userId, workspaceId, writeopiaDb, debug) {
                val multipart = call.receiveMultipart()

                val imageUrl = imageStorageService.uploadImage(multipart, userId)

                if (imageUrl != null) {
                    call.respond(HttpStatusCode.Created, ImageUploadRequest(imageUrl))
                } else {
                    call.respond(HttpStatusCode.BadRequest, "No image found in request")
                }
            }
        }
    }
}

