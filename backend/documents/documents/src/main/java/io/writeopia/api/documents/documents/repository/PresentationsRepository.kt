@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents.repository

import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.models.presentation.Slide
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.Decoration
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sql.Presentation_entity
import io.writeopia.sql.Presentation_step_entity
import io.writeopia.sql.WriteopiaDbBackend
import java.math.BigDecimal
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Presentations are stored like documents: one row per step. The title of each slide is a
 * `TITLE` step at position 0 of its slide, the content comes after it.
 */
fun WriteopiaDbBackend.savePresentation(presentation: Presentation) {
    transaction {
        presentationEntityQueries.insert(
            id = presentation.id,
            document_id = presentation.documentId,
            workspace_id = presentation.workspaceId,
            user_id = presentation.userId ?: "",
            title = presentation.title,
            created_at = presentation.createdAt.toEpochMilliseconds()
        )
        presentationStepEntityQueries.deleteByPresentationId(presentation.id)
        presentation.slides.forEachIndexed { slideIndex, slide ->
            val titleStep = StoryStep(type = StoryTypes.TITLE.type, text = slide.title)
            insertPresentationStep(titleStep, presentation.id, slideIndex, 0.0)
            slide.content.forEachIndexed { index, step ->
                insertPresentationStep(step, presentation.id, slideIndex, (index + 1).toDouble())
            }
        }
    }
}

fun WriteopiaDbBackend.getPresentationById(id: String, workspaceId: String): Presentation? =
    presentationEntityQueries.selectById(id, workspaceId)
        .executeAsOneOrNull()
        ?.let { entity -> entity.toModel(loadSlides(entity.id)) }

fun WriteopiaDbBackend.getPresentationsByDocumentId(documentId: String, workspaceId: String): List<Presentation> =
    presentationEntityQueries.selectByDocumentId(documentId, workspaceId)
        .executeAsList()
        .map { entity -> entity.toModel(loadSlides(entity.id)) }

/** Removes the presentation and its steps; false when it doesn't exist in the workspace. */
fun WriteopiaDbBackend.deletePresentation(id: String, workspaceId: String): Boolean {
    val exists = presentationEntityQueries.selectById(id, workspaceId).executeAsOneOrNull() != null
    if (!exists) return false
    transaction {
        presentationStepEntityQueries.deleteByPresentationId(id)
        presentationEntityQueries.deleteById(id, workspaceId)
    }
    return true
}

private fun WriteopiaDbBackend.insertPresentationStep(
    step: StoryStep,
    presentationId: String,
    slideIndex: Int,
    position: Double
) {
    presentationStepEntityQueries.insert(
        // The row id comes from where the step sits, not from the parsed step: unique by
        // construction, and the same on every save of the presentation.
        id = "$presentationId-$slideIndex-${position.toInt()}",
        presentation_id = presentationId,
        slide_index = slideIndex,
        type = step.type.number,
        text = step.text,
        checked = step.checked ?: false,
        position = BigDecimal.valueOf(position),
        url = step.url,
        path = step.path,
        tags = step.tags.joinToString(separator = ",") { it.tag.label },
        spans = step.spans.joinToString(separator = ",") { it.toText() },
        background_color = step.decoration.backgroundColor
    )
}

private fun WriteopiaDbBackend.loadSlides(presentationId: String): List<Slide> {
    val rows = presentationStepEntityQueries.selectByPresentationId(presentationId).executeAsList()
    return rows.groupBy { it.slide_index }
        .toSortedMap()
        .values
        .map { slideRows ->
            val title = slideRows.firstOrNull { it.type == StoryTypes.TITLE.type.number }?.text ?: ""
            val content = slideRows
                .filter { it.type != StoryTypes.TITLE.type.number }
                .sortedBy { it.position }
                .map { it.toStoryStep() }
            Slide(title = title, content = content)
        }
}

private fun Presentation_entity.toModel(slides: List<Slide>) =
    Presentation(
        id = id,
        documentId = document_id,
        workspaceId = workspace_id,
        userId = user_id.ifEmpty { null },
        title = title,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        slides = slides
    )

private fun Presentation_step_entity.toStoryStep() =
    StoryStep(
        id = id,
        type = StoryTypes.fromNumber(type).type,
        text = text,
        checked = checked,
        url = url,
        path = path,
        tags = tags.split(",").filter { it.isNotEmpty() }.mapNotNull(TagInfo.Companion::fromString).toSet(),
        spans = spans.split(",").filter { it.isNotEmpty() }.map(SpanInfo::fromString).toSet(),
        decoration = Decoration(backgroundColor = background_color),
        dbPosition = position.toDouble()
    )
