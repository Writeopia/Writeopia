@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package io.writeopia.account.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.core.folders.repository.tag.DocumentTagRepository
import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The tag catalogue of the workspace, to create, edit and delete tags. Every change is stored
 * locally first and sent to the backend right away; when sending fails, the workspace sync sends
 * it later, like the tags created in the editor. A deleted tag is a tombstone, so the removal
 * reaches the other devices, and the documents stop showing it.
 */
class WorkspaceTagsController(
    private val scope: CoroutineScope,
    private val tagRepository: DocumentTagRepository,
    private val workspaceId: suspend () -> String,
    /** Sends tags to the backend; an error outside an online workspace. */
    private val sendTags: suspend (List<DocumentTag>, String) -> ResultData<Unit>,
) {
    /** The live tags of the workspace, by name. */
    val tags: Flow<List<DocumentTag>> = flow { emit(workspaceId()) }
        .flatMapLatest { workspaceId -> tagRepository.listenForTags(workspaceId) }

    fun createTag(name: String, description: String) {
        if (name.isBlank()) return

        scope.launch {
            val tag = DocumentTag.create(name, description, workspaceId())
            tagRepository.upsertTag(tag)
            send(tag)
        }
    }

    fun editTag(id: String, name: String, description: String) {
        if (name.isBlank()) return

        scope.launch {
            val tag = tagRepository.getTagById(id)?.takeIf { tag -> !tag.deleted } ?: return@launch
            val edited = tag.copy(
                name = name.trim(),
                description = description.trim(),
                lastUpdatedAt = Clock.System.now()
            )
            if (edited.name == tag.name && edited.description == tag.description) return@launch

            tagRepository.upsertTag(edited)
            send(edited)
        }
    }

    fun deleteTag(id: String) {
        scope.launch {
            val workspaceId = workspaceId()
            tagRepository.softDeleteTag(id, workspaceId)
            val tombstone = tagRepository.getTagById(id)?.takeIf { tag -> tag.deleted } ?: return@launch
            send(tombstone)
        }
    }

    /** Marks the tag synced once the backend has it, unless it changed again meanwhile. */
    private suspend fun send(tag: DocumentTag) {
        val result = try {
            sendTags(listOf(tag), tag.workspaceId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultData.Error(e)
        }

        if (result !is ResultData.Complete) return

        val stored = tagRepository.getTagById(tag.id) ?: return
        if (stored.lastUpdatedAt <= tag.lastUpdatedAt) {
            tagRepository.upsertTag(stored.copy(lastSyncedAt = maxOf(Clock.System.now(), stored.lastUpdatedAt)))
        }
    }

    companion object {
        /** Whether another tag of [tags] already has [name], ignoring case and spaces around it. */
        fun isNameTaken(tags: List<DocumentTag>, name: String, exceptId: String? = null): Boolean {
            val wanted = name.trim()
            return wanted.isNotEmpty() &&
                tags.any { tag -> tag.id != exceptId && tag.name.trim().equals(wanted, ignoreCase = true) }
        }
    }
}

class WorkspaceTagsViewModel(
    tagRepository: DocumentTagRepository,
    workspaceId: suspend () -> String,
    sendTags: suspend (List<DocumentTag>, String) -> ResultData<Unit>,
) : ViewModel() {
    private val controller = WorkspaceTagsController(viewModelScope, tagRepository, workspaceId, sendTags)

    val tags: Flow<List<DocumentTag>> = controller.tags

    fun createTag(name: String, description: String) = controller.createTag(name, description)

    fun editTag(id: String, name: String, description: String) = controller.editTag(id, name, description)

    fun deleteTag(id: String) = controller.deleteTag(id)
}
