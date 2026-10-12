@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.serialization.extensions

import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.sdk.serialization.data.DocumentTagApi
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun DocumentTag.toApi(): DocumentTagApi =
    DocumentTagApi(
        id = id,
        workspaceId = workspaceId,
        name = name,
        description = description,
        createdAt = createdAt.toEpochMilliseconds(),
        lastUpdatedAt = lastUpdatedAt.toEpochMilliseconds(),
        deleted = deleted,
    )

/** The tag as the backend holds it: [DocumentTag.lastSyncedAt] is local knowledge and starts empty. */
fun DocumentTagApi.toModel(): DocumentTag =
    DocumentTag(
        id = id,
        workspaceId = workspaceId,
        name = name,
        description = description,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        lastUpdatedAt = Instant.fromEpochMilliseconds(lastUpdatedAt),
        lastSyncedAt = null,
        deleted = deleted,
    )
