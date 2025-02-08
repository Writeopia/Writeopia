package io.writeopia.api.documents.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.writeopia.api.documents.getDocumentById
import io.writeopia.api.documents.getDocumentsByParentId
import io.writeopia.api.documents.getIdsByParentId
import io.writeopia.api.documents.saveDocument
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.sql.WriteopiaDb

fun Routing.documentsRoute(writeopiaDb: WriteopiaDb) {
    route("/${EndPoints.documents()}") {
        get("{id}") {
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
                    message = "Not Found"
                )
            }
        }

        get("parent/{id}") {
            val id = call.pathParameters["id"]!!

            val documentList = writeopiaDb.getDocumentsByParentId(id)

            if (documentList.isNotEmpty()) {
                call.respond(
                    status = HttpStatusCode.OK,
                    message = documentList.map { it.toApi() }
                )
            } else {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = "Not Found"
                )
            }
        }

        get("parent/id/{id}") {
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
                    message = "Not Found"
                )
            }
        }

        post<DocumentApi>("") { documentApi ->
            try {
                writeopiaDb.saveDocument(documentApi.toModel())

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
    }
}
