package io.writeopia.sdk.persistence.sqldelight.dao.sql

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.persistence.sqldelight.dao.DocumentSqlDao
import io.writeopia.sdk.sql.WriteopiaDb
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlDelightDocumentRepositoryTest {

    private val database: WriteopiaDb =
        WriteopiaDb(
            JdbcSqliteDriver(
                "jdbc:sqlite:",
                Properties(),
                WriteopiaDb.Schema.synchronous()
            )
        )
    private val documentSqlDao: DocumentSqlDao = DocumentSqlDao(
        database.documentEntityQueries,
        database.storyStepEntityQueries,
    )

    private val documentRepository = SqlDelightDocumentRepository(documentSqlDao)

    @Test
    fun `when saving a document when less content, the exceeding content should be erased`() =
        runTest {
            val smallContent = mapOf(
                0 to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "text"
                ),
                1 to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "text1"
                )
            )

            val bigContent = smallContent + (
                2 to StoryStep(type = StoryTypes.TEXT.type, text = "text2")
            )

            val instant = Instant.parse("2023-01-01T12:05:30Z")
            val userId = "disconnected_user"
            val document = Document(
                createdAt = instant,
                lastUpdatedAt = instant,
                userId = userId,
                parentId = "",
                content = bigContent
            )

            documentRepository.saveDocument(document)
            val result = documentRepository.loadDocumentById(document.id)

            assertEquals(result, document)

            val newDocument = document.copy(content = smallContent)
            documentRepository.saveDocument(newDocument)
            val result1 = documentRepository.loadDocumentById(newDocument.id)

            assertEquals(result1?.content, smallContent)
        }
}
