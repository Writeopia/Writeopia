package io.writeopia.sdk.serialization.data

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
data class DocumentApi(
    val id: String = "",
    val title: String = "",
    val userId: String,
    val content: List<StoryStepApi> = emptyList(),
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val lastUpdatedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val parentId: String? = null,
    val isLocked: Boolean = false,
    val icon: IconApi? = null
)
