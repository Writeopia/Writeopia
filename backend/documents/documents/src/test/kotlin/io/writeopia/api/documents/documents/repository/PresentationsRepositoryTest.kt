@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents.repository

import io.writeopia.api.documents.documents.configureTestPersistence
import io.writeopia.sdk.import.markdown.PresentationMarkdownParser
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.models.story.StoryTypes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class PresentationsRepositoryTest {

    private val db = configureTestPersistence()
    private val workspaceId = "workspace-presentations"

    private val sample = """
        ## Slide 1

        This is a description explaining why the sky is **blue**!
        ---

        ### Slide 2

        [] This is a check box!
        - This is a list item!
        ---

        ### Thank you!!
        The presentation is over!
    """.trimIndent()

    private fun presentation(documentId: String, title: String, createdAt: Long) =
        Presentation(
            documentId = documentId,
            workspaceId = workspaceId,
            userId = "user-1",
            title = title,
            createdAt = Instant.fromEpochMilliseconds(createdAt),
            slides = PresentationMarkdownParser.parse(sample)
        )

    @AfterTest
    fun tearDown() {
        db.presentationEntityQueries.selectIdsByWorkspaceId(workspaceId).executeAsList().forEach { id ->
            db.deletePresentation(id, workspaceId)
        }
    }

    @Test
    fun `saves every step as a row and reads them back`() {
        val saved = presentation("doc-1", "Slide 1", 10)

        db.savePresentation(saved)
        val loaded = db.getPresentationById(saved.id, workspaceId)

        assertEquals(saved.id, loaded?.id)
        assertEquals("doc-1", loaded?.documentId)
        assertEquals(listOf("Slide 1", "Slide 2", "Thank you!!"), loaded?.slides?.map { it.title })
        assertEquals(
            listOf(StoryTypes.CHECK_ITEM.type, StoryTypes.UNORDERED_LIST_ITEM.type),
            loaded?.slides?.get(1)?.content?.map { it.type }
        )
        assertEquals("This is a description explaining why the sky is blue!", loaded?.slides?.get(0)?.content?.first()?.text)
        assertTrue(loaded?.slides?.get(0)?.content?.first()?.spans?.isNotEmpty() == true, "the bold span survives the round trip")
    }

    @Test
    fun `lists the presentations of a document newest first`() {
        db.savePresentation(presentation("doc-2", "Old", 1))
        db.savePresentation(presentation("doc-2", "Recent", 2))
        db.savePresentation(presentation("doc-other", "Other", 3))

        val listed = db.getPresentationsByDocumentId("doc-2", workspaceId)

        assertEquals(listOf("Recent", "Old"), listed.map { it.title })
        assertTrue(listed.all { it.slides.size == 3 })
    }

    @Test
    fun `a presentation of another workspace is not visible`() {
        val saved = presentation("doc-3", "Hidden", 1)
        db.savePresentation(saved)

        assertNull(db.getPresentationById(saved.id, "another-workspace"))
        assertTrue(db.getPresentationsByDocumentId("doc-3", "another-workspace").isEmpty())
    }

    @Test
    fun `deleting removes the presentation and its steps`() {
        val saved = presentation("doc-4", "Gone", 1)
        db.savePresentation(saved)

        assertTrue(db.deletePresentation(saved.id, workspaceId))

        assertNull(db.getPresentationById(saved.id, workspaceId))
        assertTrue(db.presentationStepEntityQueries.selectByPresentationId(saved.id).executeAsList().isEmpty())
        assertFalse(db.deletePresentation(saved.id, workspaceId))
    }
}
