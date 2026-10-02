@file:OptIn(ExperimentalTime::class)

package io.writeopia.editor.features.presentation.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import io.writeopia.core.presentations.PresentationsStore
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.models.presentation.Slide
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.serialization.data.StoryStepApi
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.app.sql.PresentationEntityQueries
import io.writeopia.app.sql.PresentationStepEntityQueries
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The presentations the local AI generates, kept in the app database like the documents: one
 * row per step, the title of each slide as a `TITLE` step at position 0 of its slide. The
 * queries are null where the database doesn't exist (Android), and then nothing is kept.
 */
class PresentationSqlStore(
    private val presentationQueries: PresentationEntityQueries?,
    private val stepQueries: PresentationStepEntityQueries?,
    private val json: Json = writeopiaJson
) : PresentationsStore {

    override suspend fun presentations(documentId: String, workspaceId: String): ResultData<List<Presentation>> =
        run {
            presentationQueries?.selectByDocumentId(documentId, workspaceId)?.awaitAsList().orEmpty().map { entity ->
                Presentation(
                    id = entity.id,
                    documentId = entity.document_id,
                    workspaceId = entity.workspace_id,
                    title = entity.title,
                    createdAt = Instant.fromEpochMilliseconds(entity.created_at),
                    slides = slides(entity.id)
                )
            }
        }

    override suspend fun presentation(id: String, workspaceId: String): ResultData<Presentation?> =
        run {
            presentationQueries?.selectById(id, workspaceId)?.awaitAsOneOrNull()?.let { entity ->
                Presentation(
                    id = entity.id,
                    documentId = entity.document_id,
                    workspaceId = entity.workspace_id,
                    title = entity.title,
                    createdAt = Instant.fromEpochMilliseconds(entity.created_at),
                    slides = slides(entity.id)
                )
            }
        }

    override suspend fun savePresentation(presentation: Presentation): ResultData<Unit> =
        run {
            val presentationQueries = presentationQueries ?: return@run
            val stepQueries = stepQueries ?: return@run
            presentationQueries.insert(
                id = presentation.id,
                document_id = presentation.documentId,
                workspace_id = presentation.workspaceId,
                title = presentation.title,
                created_at = presentation.createdAt.toEpochMilliseconds()
            )
            stepQueries.deleteByPresentationId(presentation.id)
            presentation.slides.forEachIndexed { slideIndex, slide ->
                val title = StoryStep(id = "${presentation.id}-$slideIndex-title", type = StoryTypes.TITLE.type, text = slide.title)
                insertStep(stepQueries, title, presentation.id, slideIndex, 0.0)
                slide.content.forEachIndexed { index, step ->
                    insertStep(stepQueries, step, presentation.id, slideIndex, (index + 1).toDouble())
                }
            }
        }

    override suspend fun deletePresentation(id: String, workspaceId: String): ResultData<Unit> =
        run {
            stepQueries?.deleteByPresentationId(id)
            presentationQueries?.deleteById(id)
        }

    private suspend fun insertStep(
        stepQueries: PresentationStepEntityQueries,
        step: StoryStep,
        presentationId: String,
        slideIndex: Int,
        position: Double
    ) {
        stepQueries.insert(
            id = step.id,
            presentation_id = presentationId,
            slide_index = slideIndex.toLong(),
            position = position,
            content = json.encodeToString(StoryStepApi.serializer(), step.toApi(position))
        )
    }

    private suspend fun slides(presentationId: String): List<Slide> {
        val rows = stepQueries?.selectByPresentationId(presentationId)?.awaitAsList().orEmpty()
        return rows.groupBy { it.slide_index }
            .entries
            .sortedBy { it.key }
            .map { (_, slideRows) ->
                val steps = slideRows.sortedBy { it.position }.map { row ->
                    json.decodeFromString(StoryStepApi.serializer(), row.content).toModel()
                }
                Slide(
                    title = steps.firstOrNull { it.type == StoryTypes.TITLE.type }?.text ?: "",
                    content = steps.filter { it.type != StoryTypes.TITLE.type }
                )
            }
    }

    private inline fun <T> run(block: () -> T): ResultData<T> =
        try {
            ResultData.Complete(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultData.Error(e)
        }
}
