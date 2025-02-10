package io.writeopia.sdk.search

import io.writeopia.sdk.models.document.Document

interface DocumentSearch {

    suspend fun search(query: String): List<Document>

    suspend fun getLastUpdatedAt(): List<Document>
}
