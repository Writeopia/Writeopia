package io.writeopia.api.documents.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.writeopia.api.core.auth.getUserId
import io.writeopia.api.documents.documents.DocumentsService
import io.writeopia.sdk.serialization.json.SendDocumentsRequest
import io.writeopia.api.documents.documents.repository.folderDiff
import io.writeopia.api.documents.documents.repository.getDocumentsByParentId
import io.writeopia.api.documents.documents.repository.getIdsByParentId
import io.writeopia.connection.ResultData
import io.writeopia.connection.map
import io.writeopia.sdk.models.api.request.documents.FolderDiffRequest
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sql.WriteopiaDbBackend
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
                        documentList.map { it.toModel() },
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
                println("loading diff")
                println("user id: ${getUserId()}")
                println("last sync: ${Instant.fromEpochMilliseconds(folderDiff.lastFolderSync)}")

                val documents =
                    writeopiaDb.folderDiff(
                        folderDiff.folderId,
                        getUserId() ?: "",
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

