package io.writeopia.sdk.serialization.extensions

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.Decoration
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryType
import io.writeopia.sdk.serialization.data.DecorationApi
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.data.FolderApi
import io.writeopia.sdk.serialization.data.StoryStepApi
import io.writeopia.sdk.serialization.data.StoryTypeApi
import kotlinx.datetime.Instant

fun StoryStep.toApi(position: Int): StoryStepApi =
    StoryStepApi(
        id = this.id,
        type = this.type.toApi(),
        parentId = this.parentId,
        url = this.url,
        path = this.path,
        text = this.text,
        checked = this.checked,
        steps = this.steps.map { storyStep -> storyStep.toApi(position) },
        tags = this.tags.mapTo(mutableSetOf()) { it.toApi() },
        spans = this.spans.mapTo(mutableSetOf()) { it.toApi() },
        decoration = this.decoration.toApi(),
        position = position,
        documentLink = this.documentLink?.toApi()
    )

fun StoryStepApi.toModel(): StoryStep =
    StoryStep(
        id = id,
        localId = GenerateId.generate(),
        type = type.toModel(),
        parentId = parentId,
        url = url,
        path = path,
        text = text,
        checked = checked,
        tags = this.tags.mapTo(mutableSetOf()) { it.toModel() },
        spans = this.spans.mapTo(mutableSetOf()) { it.toModel() },
        steps = steps.map { it.toModel() },
        decoration = decoration.toModel(),
        documentLink = this.documentLink?.toModel()
    )

fun StoryType.toApi(): StoryTypeApi =
    StoryTypeApi(
        name = this.name,
        number = this.number,
    )

fun StoryTypeApi.toModel(): StoryType =
    StoryType(
        name = this.name,
        number = this.number,
    )

fun Decoration.toApi(): DecorationApi = DecorationApi(
    this.backgroundColor
)

fun DecorationApi.toModel(): Decoration = Decoration(
    this.backgroundColor,
)

fun Document.toApi(): DocumentApi =
    DocumentApi(
        id = id,
        title = title,
        content = content.map { (position, story) -> story.toApi(position) },
        createdAt = createdAt.toEpochMilliseconds(),
        lastUpdatedAt = lastUpdatedAt.toEpochMilliseconds(),
        lastSyncedAt = lastSyncedAt?.toEpochMilliseconds(),
        userId = userId,
        parentId = parentId,
        isLocked = isLocked,
        icon = icon?.toApi()
    )

fun DocumentApi.toModel(): Document =
    Document(
        id = id,
        title = title,
        content = content.associateBy { it.position }.mapValues { (_, story) -> story.toModel() },
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        lastUpdatedAt = Instant.fromEpochMilliseconds(lastUpdatedAt),
        lastSyncedAt = Instant.fromEpochMilliseconds(lastUpdatedAt),
        userId = userId,
        parentId = parentId ?: "",
        isLocked = isLocked,
        icon = icon?.toModel()
    )

fun FolderApi.toModel(): Folder = Folder(
    id = id,
    parentId = parentId,
    title = title,
    createdAt = createdAt,
    lastUpdatedAt = lastUpdatedAt,
    userId = userId,
    favorite = favorite,
    icon = icon?.toModel(),
    itemCount = itemCount,
)

fun Folder.toApi(): FolderApi = FolderApi(
    id = id,
    parentId = parentId,
    title = title,
    createdAt = createdAt,
    lastUpdatedAt = lastUpdatedAt,
    userId = userId,
    favorite = favorite,
    icon = icon?.toApi(),
    itemCount = itemCount,
)
