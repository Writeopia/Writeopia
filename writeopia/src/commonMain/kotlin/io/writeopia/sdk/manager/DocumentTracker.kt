package io.writeopia.sdk.manager

import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.comment.CommentConversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Saves the document automatically based of content changes.
 */
interface DocumentTracker {

    /**
     * Saves both the state of the document using [StoryState] and also the meta information with
     * [DocumentInfo]. A flow should be provided that notifies about the changes in the document.
     */
    suspend fun saveOnStoryChanges(
        documentEditionFlow: Flow<Pair<StoryState, DocumentInfo>>,
        workspaceIdFlow: Flow<String>
    )

    suspend fun saveOnStoryChanges(
        documentEditionFlow: Flow<Pair<StoryState, DocumentInfo>>,
        workspaceIdFlow: Flow<String>,
        commentConversationsFlow: StateFlow<List<CommentConversation>>
    ) {
        saveOnStoryChanges(documentEditionFlow, workspaceIdFlow)
    }
}
