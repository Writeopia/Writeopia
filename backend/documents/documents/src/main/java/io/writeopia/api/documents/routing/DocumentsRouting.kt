package io.writeopia.api.documents.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.writeopia.api.core.auth.routing.getUserId
import io.writeopia.api.documents.documents.DocumentsService
import io.writeopia.api.documents.documents.repository.allFoldersByWorkspaceId
import io.writeopia.api.documents.documents.repository.documentsDiffByFolder
import io.writeopia.api.documents.documents.repository.documentsDiffByWorkspace
import io.writeopia.sdk.serialization.json.SendDocumentsRequest
import io.writeopia.api.documents.documents.repository.getDocumentsByParentId
import io.writeopia.api.documents.documents.repository.getIdsByParentId
import io.writeopia.connection.ResultData
import io.writeopia.connection.map
import io.writeopia.sdk.models.api.request.documents.FolderDiffRequest
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.request.WorkspaceDiffRequest
import io.writeopia.sdk.serialization.request.WorkspaceDiffResponse
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

fun Routing.documentsRoute(
    writeopiaDb: WriteopiaDbBackend,
    useAi: Boolean,
    debug: Boolean = false
) {
    authenticate("auth-jwt", optional = debug) {
        route("/api/document") {
            get("/{id}") {
                val id = call.pathParameters["id"]!!
                val userId = getUserId()

                val document = DocumentsService.getDocumentById(id, userId ?: "", writeopiaDb)

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
        get("/api/document/parent/{parentId}") {
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

    authenticate("auth-jwt", optional = debug) {
        get("/api/parent/{id}") {
            val id = call.pathParameters["id"]!!
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

    authenticate("auth-jwt", optional = debug) {
        get("/api/search") {
            val query = call.queryParameters["q"]
            val user = call.queryParameters["user"]

            if (query == null || user == null) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                val result = DocumentsService.search(query, user, writeopiaDb).map { resultData ->
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

    authenticate("auth-jwt", optional = debug) {
        post<SendDocumentsRequest>("/api/document") { request ->
            val documentList = request.documents

            try {
                if (documentList.isNotEmpty()) {
                    val addedToHub = DocumentsService.receiveDocuments(
                        documentList.map { document ->
                            document
                                .toModel()
                                .copy(lastSyncedAt = Clock.System.now())
                        },
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

    authenticate("auth-jwt", optional = debug) {
        post<FolderDiffRequest>("/api/document/folder/diff") { folderDiff ->
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

    authenticate("auth-jwt", optional = debug) {
        post<WorkspaceDiffRequest>("/api/workspace/diff") { workspaceDiff ->
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

