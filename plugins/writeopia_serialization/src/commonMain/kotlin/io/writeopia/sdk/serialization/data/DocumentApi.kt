@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.serialization.data

import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

@Serializable
data class DocumentApi(
    val id: String = "",
    val title: String = "",
    val workspaceId: String,
    val content: List<StoryStepApi> = emptyList(),
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val lastUpdatedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val isFavorite: Boolean = false,
    val lastSyncedAt: Long? = null,
    val parentId: String? = null,
    val isLocked: Boolean = false,
    val icon: IconApi? = null,
    val deleted: Boolean = false
)
