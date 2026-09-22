package io.writeopia.sdk.manager

import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.comment.CommentConversation
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
        coroutineScope {
            check(commentConversationsFlow.value.isEmpty()) {
                "This DocumentTracker does not support comment conversations."
            }

            val commentGuard = launch(start = CoroutineStart.UNDISPATCHED) {
                commentConversationsFlow.collect { conversations ->
                    check(conversations.isEmpty()) {
                        "This DocumentTracker does not support comment conversations."
                    }
                }
            }

            try {
                saveOnStoryChanges(documentEditionFlow, workspaceIdFlow)
            } finally {
                commentGuard.cancel()
            }
        }
    }
}
