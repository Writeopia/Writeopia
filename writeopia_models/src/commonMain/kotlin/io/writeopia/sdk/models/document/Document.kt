@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.models.document

import io.writeopia.sdk.models.comment.CommentConversation
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.StoryStep
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class Document(
    override val id: String = GenerateId.generate(),
    override val title: String = "",
    val content: Map<Double, StoryStep> = emptyMap(),
    val commentConversations: List<CommentConversation> = emptyList(),
    override val createdAt: Instant,
    override val lastUpdatedAt: Instant,
    val lastSyncedAt: Instant?,
    override val workspaceId: String,
    override val parentId: String,
    override val favorite: Boolean = false,
    override val icon: MenuItem.Icon? = null,
    val isLocked: Boolean = false,
    val deleted: Boolean = false,
    val published: Boolean = false
) : MenuItem
