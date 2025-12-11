@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.serialization.data

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

@Serializable
data class FolderApi(
    val id: String,
    val parentId: String,
    val title: String,
    val createdAt: Instant,
    val lastUpdatedAt: Instant,
    val workspaceId: String,
    val favorite: Boolean = false,
    val icon: IconApi? = null,
    val itemCount: Long,
)
