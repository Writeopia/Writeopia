@file:OptIn(ExperimentalTime::class)

package io.writeopia.documents.graph

import io.writeopia.core.folders.repository.folder.InMemoryFolderRepository
import io.writeopia.core.folders.repository.tag.InMemoryDocumentTagRepository
import io.writeopia.documents.graph.extensions.toGraph
import io.writeopia.documents.graph.repository.GraphRepository
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.sdk.persistence.core.repository.InMemoryDocumentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class GraphRepositoryTest {

    private val workspaceId = "ws"

    private fun document(id: String, tagIds: Set<String>) = Document(
        id = id,
        title = "Doc $id",
        createdAt = Instant.fromEpochMilliseconds(1),
        lastUpdatedAt = Instant.fromEpochMilliseconds(2),
        lastSyncedAt = null,
        workspaceId = workspaceId,
        parentId = "root",
        tagIds = tagIds,
    )

    @Test
    fun `documents hang from each of their tags and the untagged ones from the root`() = runTest {
        val documents = InMemoryDocumentRepository()
        val tags = InMemoryDocumentTagRepository()
        tags.upsertTag(DocumentTag.create("Work", "", workspaceId).copy(id = "work"))
        tags.upsertTag(DocumentTag.create("Ideas", "", workspaceId).copy(id = "ideas"))
        tags.upsertTag(DocumentTag.create("Gone", "", workspaceId).copy(id = "gone", deleted = true))
        documents.saveDocument(document("a", setOf("work", "ideas")))
        documents.saveDocument(document("b", setOf("work")))
        documents.saveDocument(document("c", emptySet()))
        documents.saveDocument(document("d", setOf("gone")))

        val repository = GraphRepository(InMemoryFolderRepository(), documents, tags)
        val adjacency = repository.loadDocumentsByTagsAsAdjacencyList(workspaceId).mapKeys { it.key.id }

        assertEquals(setOf("root", "work", "ideas"), adjacency.keys)
        assertEquals(listOf("a", "b"), adjacency.getValue("work").map { it.id }.sorted())
        assertEquals(listOf("a"), adjacency.getValue("ideas").map { it.id })
        assertEquals(listOf("c", "d", "ideas", "work"), adjacency.getValue("root").map { it.id }.sorted())
        assertEquals(listOf("Ideas", "Work"), adjacency.getValue("root").filter { it.isFolder }.map { it.title }.sorted())
    }

    @Test
    fun `a document under several tags is one node with one link per tag`() = runTest {
        val documents = InMemoryDocumentRepository()
        val tags = InMemoryDocumentTagRepository()
        tags.upsertTag(DocumentTag.create("Work", "", workspaceId).copy(id = "work"))
        tags.upsertTag(DocumentTag.create("Ideas", "", workspaceId).copy(id = "ideas"))
        documents.saveDocument(document("a", setOf("work", "ideas")))

        val repository = GraphRepository(InMemoryFolderRepository(), documents, tags)
        val graph = repository.loadDocumentsByTagsAsAdjacencyList(workspaceId)
            .mapKeys { it.key.id }
            .toGraph(1000f, 1000f)

        // The root is only a key, never a drawn node, like in the folder graph.
        assertEquals(listOf("a", "ideas", "work"), graph.nodes.map { it.id }.sorted())
        assertEquals(
            setOf("work->a", "ideas->a"),
            graph.links.map { "${it.source.id}->${it.target.id}" }.toSet(),
        )
    }
}

class GraphRepositoryDetailsTest {

    private val workspaceId = "ws"

    @Test
    fun `the details tell a tag, a folder and a document apart`() = runTest {
        val documents = InMemoryDocumentRepository()
        val tags = InMemoryDocumentTagRepository()
        tags.upsertTag(DocumentTag.create("Work", "Office things", workspaceId).copy(id = "work"))
        documents.saveDocument(
            Document(
                id = "a",
                title = "Doc a",
                createdAt = Instant.fromEpochMilliseconds(1),
                lastUpdatedAt = Instant.fromEpochMilliseconds(2),
                lastSyncedAt = null,
                workspaceId = workspaceId,
                parentId = "root",
                tagIds = setOf("work"),
            )
        )
        val repository = GraphRepository(InMemoryFolderRepository(), documents, tags)

        val tag = repository.loadDetails("work", workspaceId) as GraphItemDetails.TagDetails
        assertEquals("Work", tag.title)
        assertEquals("Office things", tag.description)
        assertEquals(listOf("a"), tag.documentIds)

        val document = repository.loadDetails("a", workspaceId) as GraphItemDetails.DocumentDetails
        assertEquals("Doc a", document.title)
        assertEquals(listOf("Work"), document.tagNames)
        assertEquals(null, document.folderName)

        assertEquals(null, repository.loadDetails("missing", workspaceId))
    }
}
