@file:OptIn(ExperimentalTime::class)

package io.writeopia.editor.features.presentation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.writeopia.editor.features.presentation.repository.PresentationSqlStore
import io.writeopia.sdk.import.markdown.PresentationMarkdownParser
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sqldelight.database.DatabaseFactory.createDatabase
import io.writeopia.sqldelight.database.driver.DriverFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class PresentationSqlStoreTest {

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

    private suspend fun store(): PresentationSqlStore {
        val database = createDatabase(DriverFactory(), JdbcSqliteDriver.IN_MEMORY)
        return PresentationSqlStore(database.presentationEntityQueries, database.presentationStepEntityQueries)
    }

    private fun presentation(documentId: String, title: String, createdAt: Long) = Presentation(
        documentId = documentId,
        workspaceId = "w1",
        title = title,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        slides = PresentationMarkdownParser.parse(sample)
    )

    @Test
    fun `saves every step as a row and reads them back`() = runTest {
        val store = store()
        val saved = presentation("d1", "Slide 1", 10)

        store.savePresentation(saved)
        val loaded = assertIs<ResultData.Complete<Presentation?>>(store.presentation(saved.id, "w1")).data

        // The steps get a new localId when read back, so the content is compared field by field.
        assertEquals(saved.id, loaded?.id)
        assertEquals(saved.documentId, loaded?.documentId)
        assertEquals(saved.createdAt, loaded?.createdAt)
        assertEquals(listOf("Slide 1", "Slide 2", "Thank you!!"), loaded?.slides?.map { it.title })
        assertEquals(
            saved.slides.map { slide ->
                slide.content.map { it.id to it.text }
            },
            loaded?.slides?.map { slide ->
                slide.content.map {
                    it.id to
                        it.text
                }
            }
        )
        assertEquals(
            listOf(StoryTypes.CHECK_ITEM.type, StoryTypes.UNORDERED_LIST_ITEM.type),
            loaded?.slides?.get(1)?.content?.map { it.type }
        )
        assertTrue(loaded?.slides?.get(0)?.content?.first()?.spans?.isNotEmpty() == true, "the bold span survives")
    }

    @Test
    fun `lists newest first within the workspace and deletes with the steps`() = runTest {
        val store = store()
        store.savePresentation(presentation("d1", "Old", 1))
        val recent = presentation("d1", "Recent", 2)
        store.savePresentation(recent)
        store.savePresentation(presentation("other", "Other", 3))

        val listed = assertIs<ResultData.Complete<List<Presentation>>>(store.presentations("d1", "w1")).data
        assertEquals(listOf("Recent", "Old"), listed.map { it.title })
        assertTrue(assertIs<ResultData.Complete<List<Presentation>>>(store.presentations("d1", "another")).data.isEmpty())

        store.deletePresentation(recent.id, "w1")
        assertEquals(
            listOf("Old"),
            assertIs<ResultData.Complete<List<Presentation>>>(store.presentations("d1", "w1")).data.map {
                it.title
            }
        )
    }

    @Test
    fun `without a database nothing is kept and nothing fails`() = runTest {
        val store = PresentationSqlStore(null, null)

        assertIs<ResultData.Complete<Unit>>(store.savePresentation(presentation("d1", "Sky", 1)))
        assertTrue(assertIs<ResultData.Complete<List<Presentation>>>(store.presentations("d1", "w1")).data.isEmpty())
    }
}
