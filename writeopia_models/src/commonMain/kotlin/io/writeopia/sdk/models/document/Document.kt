package io.writeopia.sdk.models.document

import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.StoryStep
import kotlin.time.Instant

data class Document(
    override val id: String = GenerateId.generate(),
    override val title: String = "",
    val content: Map<Int, StoryStep> = emptyMap(),
    override val createdAt: Instant,
    override val lastUpdatedAt: Instant,
    val lastSyncedAt: Instant?,
    override val userId: String,
    override val parentId: String,
    override val favorite: Boolean = false,
    override val icon: MenuItem.Icon? = null,
    val isLocked: Boolean = false,
    val deleted: Boolean = false
) : MenuItem
