package io.writeopia.sdk.persistence.parse

import io.writeopia.sdk.models.link.DocumentLink
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.Decoration
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryType
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.persistence.entity.story.StoryStepEntity

fun Map<Int, StoryStep>.toEntity(documentId: String): List<StoryStepEntity> =
    flatMap { (position, storyUnit) ->
        if (storyUnit.isGroup) {
            listOf(storyUnit.toEntity(position, documentId)) + storyUnit.steps.map { innerStory ->
                innerStory.copy(parentId = storyUnit.id).toEntity(position, documentId)
            }
        } else {
            listOf(storyUnit.toEntity(position, documentId))
        }
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
        documentLink = documentLink
    )

fun StoryStep.toEntity(position: Int, documentId: String): StoryStepEntity =
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
        linkToDocument = documentLink?.id
    )
