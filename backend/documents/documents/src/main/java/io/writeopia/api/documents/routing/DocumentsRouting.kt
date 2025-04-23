package io.writeopia.api.documents.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.writeopia.api.documents.documents.DocumentsService
import io.writeopia.api.documents.documents.repository.folderDiff
import io.writeopia.api.documents.documents.repository.getDocumentsByParentId
import io.writeopia.api.documents.documents.repository.getIdsByParentId
import io.writeopia.connection.ResultData
import io.writeopia.connection.map
import io.writeopia.sdk.models.api.request.documents.FolderDiffRequest
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sql.WriteopiaDbBackend

fun Routing.documentsRoute(writeopiaDb: WriteopiaDbBackend) {
    route("api/document") {
        get("/{id}") {
            val id = call.pathParameters["id"]!!

            val document = DocumentsService.getDocumentById(id, writeopiaDb)

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

        get("/parent/{parentId}") {
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

        get("/parent/id/{id}") {
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

        get("/search") {
            val query = call.queryParameters["q"]

            if (query == null) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                val result = DocumentsService.search(query, writeopiaDb).map { resultData ->
                    resultData.map { document -> document.toApi() }
                }

                if (result is ResultData.Complete) {
                    call.respond(status = HttpStatusCode.OK, message = result.data)
                } else {
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }

        post<List<DocumentApi>> { documentApiList ->
            println("Received documents!")

            try {
                val addedToHub = DocumentsService.receiveDocuments(
                    documentApiList.map { it.toModel() },
                    writeopiaDb
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

            } catch (e: Exception) {
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    message = "${e.message}"
                )
            }
        }

        post<FolderDiffRequest>("/folder/diff") { folderDiff ->
            try {
                val documents =
                    writeopiaDb.folderDiff(folderDiff.folderId, folderDiff.lastFolderSync)

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
