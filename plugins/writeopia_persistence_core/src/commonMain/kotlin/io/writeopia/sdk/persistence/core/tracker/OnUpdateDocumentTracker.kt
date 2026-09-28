@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.core.tracker

import io.writeopia.sdk.filter.DocumentFilter
import io.writeopia.sdk.filter.DocumentFilterObject
import io.writeopia.sdk.manager.DocumentTracker
import io.writeopia.sdk.manager.DocumentUpdate
import io.writeopia.sdk.manager.UnsupportedCommentConversationsException
import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class OnUpdateDocumentTracker(
    private val documentUpdate: DocumentUpdate,
    private val documentFilter: DocumentFilter = DocumentFilterObject,
    private val onStoryStepUpdate: suspend (StoryStep, Double) -> Unit = { _, _ -> },
    private val onDocumentUpdate: suspend (Document) -> Unit = {}
) : DocumentTracker {

    override suspend fun saveOnStoryChanges(
        documentEditionFlow: Flow<Pair<StoryState, DocumentInfo>>,
        workspaceIdFlow: Flow<String>,
    ) {
        val commentFreeDocumentEditionFlow = documentEditionFlow.onEach { (storyState, _) ->
            val hasCommentSpans = storyState.stories.values.any { story ->
                story.spans.any { span -> span.span == Span.COMMENT }
            }
            if (hasCommentSpans) {
                throw UnsupportedCommentConversationsException(
                    "Comment-bearing documents require the comment-aware saveOnStoryChanges overload."
                )
            }
        }

        saveOnStoryChanges(
            commentFreeDocumentEditionFlow,
            workspaceIdFlow,
            MutableStateFlow(emptyMap()),
        )
    }

    override suspend fun saveOnStoryChanges(
        documentEditionFlow: Flow<Pair<StoryState, DocumentInfo>>,
        workspaceIdFlow: Flow<String>,
        commentConversationsFlow: StateFlow<Map<String, List<Comment>>>,
    ) {
        var previousCommentConversations: Map<String, List<Comment>>? = null

        fun fullDocument(
            storyState: StoryState,
            documentInfo: DocumentInfo,
            workspaceId: String,
            commentConversations: Map<String, List<Comment>>,
        ): Document {
            val stories = storyState.stories.filter { (_, story) -> !story.ephemeral }
            val titleFromContent = stories.values
                .firstOrNull { storyStep -> storyStep.type == StoryTypes.TITLE.type }
                ?.text

            return Document(
                id = documentInfo.id,
                title = titleFromContent ?: documentInfo.title,
                content = documentFilter.removeTypesFromDocument(stories),
                createdAt = documentInfo.createdAt,
                lastUpdatedAt = Clock.System.now(),
                lastSyncedAt = documentInfo.lastSyncedAt,
                workspaceId = workspaceId,
                parentId = documentInfo.parentId,
                icon = documentInfo.icon,
                isLocked = documentInfo.isLocked,
                favorite = documentInfo.isFavorite,
                commentConversations = commentConversations,
            )
        }

        combine(
            documentEditionFlow,
            workspaceIdFlow,
            commentConversationsFlow,
        ) { documentEdition, workspaceId, commentConversations ->
            Triple(documentEdition, workspaceId, commentConversations)
        }.collect { (documentEdition, workspaceId, commentConversations) ->
            val (storyState, documentInfo) = documentEdition
            val commentsChanged = previousCommentConversations?.let { previous ->
                previous != commentConversations
            } ?: false
            previousCommentConversations = commentConversations

            if (commentsChanged) {
                withContext(NonCancellable) {
                    val document = fullDocument(
                        storyState,
                        documentInfo,
                        workspaceId,
                        commentConversations,
                    )
                    documentUpdate.saveDocument(document)
                    onDocumentUpdate(document)
                }
                return@collect
            }

            when (val lastEdit = storyState.lastEdit) {
                is LastEdit.LineEdition -> {
                    if (lastEdit.storyStep.ephemeral) return@collect

                    documentUpdate.saveStoryStep(
                        storyStep = lastEdit.storyStep.copy(
                            localId = GenerateId.generate()
                        ),
                        position = lastEdit.position,
                        documentId = documentInfo.id,
                    )

                    onStoryStepUpdate(lastEdit.storyStep, lastEdit.position)

                    val stories = storyState.stories
                    val titleFromContent = stories.values
                        .firstOrNull { storyStep ->
                            // Todo: Change the type of change to allow different types. The client code should decide what is a title
                            // It is also interesting to inv
                            storyStep.type == StoryTypes.TITLE.type
                        }?.text

                    documentUpdate.saveDocumentMetadata(
                        Document(
                            id = documentInfo.id,
                            title = titleFromContent ?: documentInfo.title,
                            createdAt = documentInfo.createdAt,
                            lastUpdatedAt = Clock.System.now(),
                            lastSyncedAt = documentInfo.lastSyncedAt,
                            workspaceId = workspaceId,
                            parentId = documentInfo.parentId,
                            icon = documentInfo.icon,
                            isLocked = documentInfo.isLocked,
                        )
                    )
                }

                LastEdit.Nothing -> {}

                LastEdit.Whole -> withContext(NonCancellable) {
                    val document = fullDocument(
                        storyState,
                        documentInfo,
                        workspaceId,
                        commentConversations,
                    )
                    documentUpdate.saveDocument(document)
                    onDocumentUpdate(document)
                }

                is LastEdit.InfoEdition -> withContext(NonCancellable) {
                    val stories = storyState.stories
                    val titleFromContent = stories.values.firstOrNull { storyStep ->
                        // Todo: Change the type of change to allow different types. The client code should decide what is a title
                        // It is also interesting to inv
                        storyStep.type == StoryTypes.TITLE.type
                    }?.text

                    documentUpdate.saveDocumentMetadata(
                        Document(
                            id = documentInfo.id,
                            title = titleFromContent ?: documentInfo.title,
                            createdAt = documentInfo.createdAt,
                            lastUpdatedAt = Clock.System.now(),
                            lastSyncedAt = documentInfo.lastSyncedAt,
                            workspaceId = workspaceId,
                            parentId = documentInfo.parentId,
                            icon = documentInfo.icon,
                            isLocked = documentInfo.isLocked
                        ),
                    )

                    if (!lastEdit.storyStep.ephemeral) {
                        documentUpdate.saveStoryStep(
                            storyStep = lastEdit.storyStep,
                            position = lastEdit.position,
                            documentId = documentInfo.id
                        )

                        onStoryStepUpdate(lastEdit.storyStep, lastEdit.position)
                    }
                }

                LastEdit.Metadata -> withContext(NonCancellable) {
                    val stories = storyState.stories
                    val titleFromContent = stories.values.firstOrNull { storyStep ->
                        // Todo: Change the type of change to allow different types. The client code should decide what is a title
                        // It is also interesting to inv
                        storyStep.type == StoryTypes.TITLE.type
                    }?.text

                    documentUpdate.saveDocumentMetadata(
                        Document(
                            id = documentInfo.id,
                            title = titleFromContent ?: documentInfo.title,
                            createdAt = documentInfo.createdAt,
                            lastUpdatedAt = Clock.System.now(),
                            lastSyncedAt = documentInfo.lastSyncedAt,
                            workspaceId = workspaceId,
                            parentId = documentInfo.parentId,
                            icon = documentInfo.icon,
                            isLocked = documentInfo.isLocked,
                            favorite = documentInfo.isFavorite
                        )
                    )
                }

                is LastEdit.LineBreakEdition -> {
                    val originalStep = lastEdit.originalStep
                    val newStep = lastEdit.newStep

                    if (!originalStep.second.ephemeral && !newStep.second.ephemeral) {
                        documentUpdate.saveStorySteps(
                            steps = listOf(originalStep, newStep),
                            documentId = documentInfo.id
                        )
                    }

                    val stories = storyState.stories
                    val titleFromContent = stories.values
                        .firstOrNull { storyStep ->
                            storyStep.type == StoryTypes.TITLE.type
                        }?.text

                    documentUpdate.saveDocumentMetadata(
                        Document(
                            id = documentInfo.id,
                            title = titleFromContent ?: documentInfo.title,
                            createdAt = documentInfo.createdAt,
                            lastUpdatedAt = Clock.System.now(),
                            lastSyncedAt = documentInfo.lastSyncedAt,
                            workspaceId = workspaceId,
                            parentId = documentInfo.parentId,
                            icon = documentInfo.icon,
                            isLocked = documentInfo.isLocked
                        )
                    )
                }

                is LastEdit.BulkEdition -> withContext(NonCancellable) {
                    val nonEphemeralSteps = lastEdit.steps.filter { (_, step) -> !step.ephemeral }

                    if (nonEphemeralSteps.isNotEmpty()) {
                        documentUpdate.saveStorySteps(
                            steps = nonEphemeralSteps,
                            documentId = documentInfo.id
                        )
                    }

                    val stories = storyState.stories
                    val titleFromContent = stories.values
                        .firstOrNull { storyStep ->
                            storyStep.type == StoryTypes.TITLE.type
                        }?.text

                    documentUpdate.saveDocumentMetadata(
                        Document(
                            id = documentInfo.id,
                            title = titleFromContent ?: documentInfo.title,
                            createdAt = documentInfo.createdAt,
                            lastUpdatedAt = Clock.System.now(),
                            lastSyncedAt = documentInfo.lastSyncedAt,
                            workspaceId = workspaceId,
                            parentId = documentInfo.parentId,
                            icon = documentInfo.icon,
                            isLocked = documentInfo.isLocked
                        )
                    )
                }

                is LastEdit.DeleteEdition -> withContext(NonCancellable) {
                    documentUpdate.deleteStoryStep(
                        storyStepId = lastEdit.deletedId,
                        documentId = documentInfo.id
                    )

                    val stories = storyState.stories
                    val titleFromContent = stories.values
                        .firstOrNull { storyStep ->
                            storyStep.type == StoryTypes.TITLE.type
                        }?.text

                    documentUpdate.saveDocumentMetadata(
                        Document(
                            id = documentInfo.id,
                            title = titleFromContent ?: documentInfo.title,
                            createdAt = documentInfo.createdAt,
                            lastUpdatedAt = Clock.System.now(),
                            lastSyncedAt = documentInfo.lastSyncedAt,
                            workspaceId = workspaceId,
                            parentId = documentInfo.parentId,
                            icon = documentInfo.icon,
                            isLocked = documentInfo.isLocked
                        )
                    )
                }

                is LastEdit.EraseEdition -> withContext(NonCancellable) {
                    // Delete the erased step
                    documentUpdate.deleteStoryStep(
                        storyStepId = lastEdit.deletedId,
                        documentId = documentInfo.id
                    )

                    // Update the previous step with merged content
                    val (dbPos, updatedStep) = lastEdit.updatedStep
                    if (!updatedStep.ephemeral) {
                        documentUpdate.saveStorySteps(
                            steps = listOf(dbPos to updatedStep),
                            documentId = documentInfo.id
                        )
                    }

                    val stories = storyState.stories
                    val titleFromContent = stories.values
                        .firstOrNull { storyStep ->
                            storyStep.type == StoryTypes.TITLE.type
                        }?.text

                    documentUpdate.saveDocumentMetadata(
                        Document(
                            id = documentInfo.id,
                            title = titleFromContent ?: documentInfo.title,
                            createdAt = documentInfo.createdAt,
                            lastUpdatedAt = Clock.System.now(),
                            lastSyncedAt = documentInfo.lastSyncedAt,
                            workspaceId = workspaceId,
                            parentId = documentInfo.parentId,
                            icon = documentInfo.icon,
                            isLocked = documentInfo.isLocked
                        )
                    )
                }

                is LastEdit.BulkDeleteEdition -> withContext(NonCancellable) {
                    lastEdit.deletedIds.forEach { deletedId ->
                        documentUpdate.deleteStoryStep(
                            storyStepId = deletedId,
                            documentId = documentInfo.id
                        )
                    }

                    val stories = storyState.stories
                    val titleFromContent = stories.values
                        .firstOrNull { storyStep ->
                            storyStep.type == StoryTypes.TITLE.type
                        }?.text

                    documentUpdate.saveDocumentMetadata(
                        Document(
                            id = documentInfo.id,
                            title = titleFromContent ?: documentInfo.title,
                            createdAt = documentInfo.createdAt,
                            lastUpdatedAt = Clock.System.now(),
                            lastSyncedAt = documentInfo.lastSyncedAt,
                            workspaceId = workspaceId,
                            parentId = documentInfo.parentId,
                            icon = documentInfo.icon,
                            isLocked = documentInfo.isLocked
                        )
                    )
                }
            }
        }
    }
}
