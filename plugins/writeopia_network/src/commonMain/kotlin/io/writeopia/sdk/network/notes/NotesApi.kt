package io.writeopia.sdk.network.notes

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel

/**
 * API for calling notes.
 */
class NotesApi(private val client: HttpClient, private val baseUrl: String) {

    /**
     * The introductory notes of the app. The first notes after login.
     */
    suspend fun introNotes(): List<Document> {
        return client.get("$baseUrl/${EndPoints.introNotes()}")
            .body<List<DocumentApi>>()
            .map { documentApi -> documentApi.toModel() }
    }

    suspend fun createDocument(document: Document): Boolean {
        return client.post("$baseUrl/${EndPoints.documents()}") {
            setBody(document.toApi())
        }.status.isSuccess()
    }
}
