package io.writeopia.sdk.persistence.parse

import io.writeopia.sdk.models.link.DocumentLink
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.Decoration
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryType
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.persistence.entity.story.StoryStepEntity

fun Map<Double, StoryStep>.toEntity(documentId: String): List<StoryStepEntity> =
    flatMap { (position, storyUnit) ->
        storyUnit.toEntityTree(storyUnit.dbPosition ?: position, documentId)
    }

private fun StoryStep.toEntityTree(
    position: Double,
    documentId: String,
    parentIdOverride: String? = parentId,
): List<StoryStepEntity> {
    val current = copy(parentId = parentIdOverride).toEntity(position, documentId)
    return listOf(current) + steps.flatMapIndexed { index, child ->
        child.toEntityTree(
            position = index.toDouble(),
            documentId = documentId,
            parentIdOverride = id,
        )
    }
}

internal fun List<StoryStepEntity>.toStoryTree(
    documentLinkTitles: Map<String, String?> = emptyMap(),
): Map<Double, StoryStep> {
    val childrenByParent = groupBy { entity -> entity.parentId }

    fun restore(entity: StoryStepEntity): StoryStep {
        val children = childrenByParent[entity.id]
            .orEmpty()
            .sortedBy { child -> child.position }
            .map(::restore)
        val documentLink = entity.linkToDocument?.let { documentId ->
            DocumentLink(documentId, documentLinkTitles[documentId])
        }

        return entity.toModel(documentLink = documentLink).copy(steps = children)
    }

    return childrenByParent[null]
        .orEmpty()
        .sortedBy { entity -> entity.position }
        .associate { entity -> entity.position to restore(entity) }
}

fun StoryStepEntity.toModel(
    steps: List<StoryStepEntity> = emptyList(),
    nameToType: (String) -> StoryType = { typeName ->
        StoryTypes.fromName(typeName).type
    },
    documentLink: DocumentLink? = null,
): StoryStep =
    StoryStep(
        id = id,
        localId = localId,
        type = nameToType(type),
        parentId = parentId,
        url = url,
        path = path,
        text = text,
        checked = checked,
        steps = steps.map { storyUnitEntity -> storyUnitEntity.toModel() },
        decoration = Decoration(
            backgroundColor = backgroundColor,
        ),
        tags = tags
            .split(",")
            .filter { it.isNotEmpty() }
            .mapNotNull(TagInfo.Companion::fromString)
            .toSet(),
        spans = spans
            .split(",")
            .filter { it.isNotEmpty() }
            .map(SpanInfo::fromString)
            .toSet(),
        documentLink = documentLink,
        dbPosition = position,
        lastUpdatedAt = lastUpdatedAt,
    )

fun StoryStep.toEntity(position: Double, documentId: String): StoryStepEntity =
    StoryStepEntity(
        id = id,
        localId = localId,
        type = type.name,
        parentId = parentId,
        url = url,
        path = path,
        text = text,
        checked = checked,
        position = position,
        documentId = documentId,
        isGroup = false,
        hasInnerSteps = this.steps.isNotEmpty(),
        backgroundColor = this.decoration.backgroundColor,
        tags = this.tags.joinToString(separator = ",") { it.tag.label },
        spans = this.spans.joinToString(separator = ",") { it.toText() },
        linkToDocument = documentLink?.id,
        lastUpdatedAt = lastUpdatedAt,
    )
