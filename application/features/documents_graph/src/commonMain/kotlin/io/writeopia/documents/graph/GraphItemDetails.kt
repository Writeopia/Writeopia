@file:OptIn(ExperimentalTime::class)

package io.writeopia.documents.graph

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** What the card at the corner of the graph says about the node the user clicked. */
sealed class GraphItemDetails {
    abstract val id: String
    abstract val title: String

    data class DocumentDetails(
        override val id: String,
        override val title: String,
        val lastUpdatedAt: Instant,
        /** The folder the document is in; null at the root. */
        val folderName: String?,
        val tagNames: List<String>,
    ) : GraphItemDetails()

    data class FolderDetails(
        override val id: String,
        override val title: String,
        val folderCount: Int,
        val documentCount: Int,
    ) : GraphItemDetails()

    data class TagDetails(
        override val id: String,
        override val title: String,
        val description: String,
        /** The ids of the documents carrying the tag, so the graph can bring them forward. */
        val documentIds: List<String>,
    ) : GraphItemDetails()
}
