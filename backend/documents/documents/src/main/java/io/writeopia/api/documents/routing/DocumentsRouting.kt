package io.writeopia.api.documents.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.writeopia.api.documents.repository.folderDiff
import io.writeopia.api.documents.repository.getDocumentById
import io.writeopia.api.documents.repository.getDocumentsByParentId
import io.writeopia.api.documents.repository.getIdsByParentId
import io.writeopia.api.documents.repository.saveDocument
import io.writeopia.sdk.models.api.request.documents.FolderDiffRequest
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sql.WriteopiaDbBackend

fun Routing.documentsRoute(writeopiaDb: WriteopiaDbBackend) {
    route("api/document") {
        get("/{id}") {
            val id = call.pathParameters["id"]!!

            val document = writeopiaDb.getDocumentById(id)

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

        post<List<DocumentApi>> { documentApiList ->
            println("Received documents!")

            try {
                documentApiList.forEach { documentApi ->
                    writeopiaDb.saveDocument(documentApi.toModel())
                }

                call.respond(
                    status = HttpStatusCode.OK,
                    message = "Accepted"
                )
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
