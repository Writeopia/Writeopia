@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.serialization.extensions

import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.models.presentation.Slide
import io.writeopia.sdk.serialization.data.PresentationApi
import io.writeopia.sdk.serialization.data.SlideApi
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun Slide.toApi(): SlideApi =
    SlideApi(
        title = title,
        content = content.mapIndexed { index, step -> step.toApi((index + 1).toDouble()) }
    )

fun SlideApi.toModel(): Slide =
    Slide(
        title = title,
        content = content.sortedBy { it.position }.map { it.toModel() }
    )

fun Presentation.toApi(): PresentationApi =
    PresentationApi(
        id = id,
        documentId = documentId,
        title = title,
        createdAt = createdAt.toEpochMilliseconds(),
        slides = slides.map { it.toApi() }
    )

fun PresentationApi.toModel(workspaceId: String, userId: String? = null): Presentation =
    Presentation(
        id = id,
        documentId = documentId,
        workspaceId = workspaceId,
        userId = userId,
        title = title,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        slides = slides.map { it.toModel() }
    )
