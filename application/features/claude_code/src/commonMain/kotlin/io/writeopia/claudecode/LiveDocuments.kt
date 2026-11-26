package io.writeopia.claudecode

import io.writeopia.sdk.export.DocumentToMarkdown
import io.writeopia.claudecode.mcp.PlaceReport
import io.writeopia.claudecode.mcp.OpenDocument
import io.writeopia.claudecode.mcp.FolderTree
import io.writeopia.claudecode.mcp.DocumentSnapshot
import io.writeopia.sdk.model.action.Action
import io.writeopia.claudecode.mcp.LiveFolder
import io.writeopia.claudecode.markdown.LiveMarkdown
import io.writeopia.core.folders.sync.ContentLoadActivity
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What Claude Code can do with the documents: create one and add to one, in the editor on
 * screen, so the person watches it happen. One call at a time: a second one waits for the first.
 */
class LiveDocuments(
    private val bridge: ClaudeCodeBridge = ClaudeCodeBridge,
    private val writer: LiveWriter = LiveWriter(),
    private val openTimeout: Duration = 15.seconds,
    private val isLoading: Flow<Boolean> = ContentLoadActivity.isLoading,
    /** The folders of the app; null where Claude can't make folders nor choose one. */
    private val folders: LiveFolders? = null,
    /** The tag catalogue of the workspace; null where the app has no tags. */
    private val tags: LiveTags? = null,
) {
    private val mutex = Mutex()

    /** The document as Claude Code last saw it (at the end of its last call), to tell what changed since. */
    private var lastSeen: DocumentSnapshot? = null

    sealed interface Outcome {
        data class Written(val documentId: String, val title: String, val blocks: Int) : Outcome

        data class Failed(val reason: String) : Outcome

        data class FolderCreated(val folderId: String, val name: String, val parentId: String) : Outcome

        data class Folders(val folders: List<LiveFolder>, val currentFolderId: String) : Outcome

        data object Finished : Outcome

        data class Tags(val tags: List<LiveTag>) : Outcome

        /** [added]: each tag put on the document, and whether it was made for it. */
        data class Tagged(
            val documentId: String,
            val title: String,
            val added: List<Pair<String, Boolean>>,
            val already: List<String>,
        ) : Outcome

        data class HeaderImageSet(val documentId: String, val title: String, val url: String) : Outcome

        /** The text of get_current_place. */
        data class Place(val text: String) : Outcome

        /** The person stopped Claude Code, in the middle of this call or a moment before it. */
        data object Stopped : Outcome
    }

    suspend fun create(title: String, markdown: String?, folderId: String? = null): Outcome = inAiMode { ticket ->
        val folder = folderId?.trim()?.takeIf { it.isNotEmpty() }
        if (folder != null && folders?.exists(folder) != true) return noFolder(folder)

        val previous = bridge.current
        bridge.requestOpen(ClaudeCodeBridge.OpenRequest.NewDocument(folder))

        val manager = awaitEditor { manager -> manager !== previous }
            ?: return Outcome.Failed(
                "Writeopia could not open a new document. Check that the app shows a workspace, not a sign-in screen."
            )

        write(manager, title.trim(), markdown.orEmpty(), ticket)
    }

    suspend fun append(documentId: String?, markdown: String): Outcome {
        switchedDocument(documentId)?.let { return it }
        return appendInAiMode(documentId, markdown)
    }

    private suspend fun appendInAiMode(documentId: String?, markdown: String): Outcome = inAiMode { ticket ->
        val manager = when (val opened = openEditor(documentId)) {
            is Opened.Editor -> opened.manager
            is Opened.Failure -> return opened.outcome
        }

        write(manager, null, markdown, ticket)
    }

    /**
     * Without a document_id, in the middle of a working session (AI mode on), the open document
     * must still be the one Claude was writing in: when the person moved to another one, Claude is
     * told where they are now, to decide again, instead of writing there unasked.
     */
    private fun switchedDocument(documentId: String?): Outcome? {
        if (!documentId.isNullOrBlank() || !bridge.aiMode.value.isOn) return null
        val last = lastSeen ?: return null
        val current = bridge.current?.takeIf { it.isInitialized() } ?: return null
        val currentId = current.documentInfo.value.id
        if (currentId == last.documentId) return null

        return Outcome.Failed(
            "The person switched documents: Writeopia shows \"${titleOf(current)}\" (document_id: $currentId) " +
                "now, not \"${last.title}\" (document_id: ${last.documentId}) where you were writing. Call " +
                "get_current_place, then pass the document_id of the document to write in."
        )
    }

    /**
     * Where the person is: the document open in the editor (with its content as Markdown when
     * [includeContent]) or the folder open, and what changed since Claude's last call. Only
     * reads: no AI mode.
     */
    suspend fun currentPlace(includeContent: Boolean): Outcome = mutex.withLock {
        val manager = bridge.current?.takeIf { it.isInitialized() }
        val allFolders = folders?.list().orEmpty()
        val byId = allFolders.associateBy { it.id }
        val now = manager?.let(::snapshot)

        val document = manager?.let {
            val info = manager.documentInfo.value
            val tagNames = tags?.list().orEmpty()
                .filter { tag -> tag.id in manager.documentTagIds.value }
                .map { tag -> tag.name }
                .sortedBy { name -> name.lowercase() }
            OpenDocument(
                id = info.id,
                title = titleOf(manager),
                folderPath = pathTo(info.parentId, byId),
                tags = tagNames,
                isLocked = info.isLocked,
                hasHeaderPicture = manager.currentStory.value.stories.values
                    .firstOrNull { step -> step.type == StoryTypes.TITLE.type }
                    ?.url.isNullOrBlank().not(),
            )
        }
        val currentFolder = folders?.currentFolderId()?.let(byId::get)
        val change = lastSeen?.changeTo(now)
        val content = manager?.takeIf { includeContent }?.let { editor ->
            DocumentToMarkdown.parse(editor.currentStory.value.stories.filterValues { step -> !step.ephemeral })
        }

        // Claude has seen it now: the next report tells what changed from here.
        if (now != null) lastSeen = now
        Outcome.Place(PlaceReport.describe(document, currentFolder, change, content))
    }

    /** The folders from the top level down to [folderId]; empty at the top level. */
    private fun pathTo(folderId: String, byId: Map<String, LiveFolder>): List<LiveFolder> {
        val path = mutableListOf<LiveFolder>()
        var id: String? = folderId
        while (id != null && id != FolderTree.ROOT && path.size < byId.size) {
            val folder = byId[id] ?: break
            path.add(0, folder)
            id = folder.parentId
        }
        return path
    }

    private fun snapshot(manager: WriteopiaStateManager): DocumentSnapshot =
        DocumentSnapshot(
            documentId = manager.documentInfo.value.id,
            title = titleOf(manager),
            blocks = manager.currentStory.value.stories.values
                .filter { step -> !step.ephemeral }
                .associate { step ->
                    step.id to "${step.type.number}|${step.text}|${step.checked}|${step.url}|${step.tags}"
                },
        )

    /**
     * Draws a header picture for the document with the cloud AI of Writeopia, from its title and
     * text and the [description] (what the picture should show, optional), and puts it in the
     * header. Refused when the header already has a picture: Claude only adds.
     */
    suspend fun setHeaderImage(documentId: String?, description: String?): Outcome = inAiMode { ticket ->
        val manager = when (val opened = openEditor(documentId)) {
            is Opened.Editor -> opened.manager
            is Opened.Failure -> return opened.outcome
        }
        if (manager.documentInfo.value.isLocked) {
            return Outcome.Failed("The document is locked in Writeopia. Unlock it to write in it.")
        }

        val (position, title) = manager.currentStory.value.stories.entries
            .firstOrNull { (_, step) -> step.type == StoryTypes.TITLE.type }
            ?.toPair()
            ?: return Outcome.Failed("The document has no header.")
        if (!title.url.isNullOrBlank()) {
            return Outcome.Failed(
                "The document already has a header picture. Only the person can change it, in the app."
            )
        }

        val painter = bridge.headerPainter(manager)
            ?: return Outcome.Failed("This Writeopia app can't draw header pictures.")
        val url = painter.draw(description?.trim().orEmpty()).getOrElse { error ->
            return Outcome.Failed(error.message ?: "The cloud AI drew no picture.")
        }
        if (bridge.isStopped(ticket)) return Outcome.Stopped

        // A picture replaces the color of the header: it has one or the other.
        val withPicture = (manager.getStory(position) ?: title).let { step ->
            step.copy(url = url, decoration = step.decoration.copy(backgroundColor = null))
        }
        manager.changeStoryState(Action.StoryStateChange(withPicture, position, preserveFocus = true))
        lastSeen = snapshot(manager)

        Outcome.HeaderImageSet(manager.documentInfo.value.id, titleOf(manager), url)
    }

    /** The tag catalogue, to reuse tags before making new ones. Only reads: no AI mode. */
    suspend fun listTags(): Outcome {
        val tags = tags ?: return Outcome.Failed("This Writeopia app has no tags.")
        return Outcome.Tags(tags.list().sortedBy { tag -> tag.name.lowercase() })
    }

    /**
     * Tags the document with [names]: a tag of the catalogue with the same name (any
     * capitalization) is reused, a new one is made in the catalogue. Tags the document already has
     * stay; none is removed.
     */
    suspend fun addTags(documentId: String?, names: List<String>): Outcome = inAiMode { ticket ->
        val tags = tags ?: return Outcome.Failed("This Writeopia app has no tags.")
        val wanted = names.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        if (wanted.isEmpty()) return Outcome.Failed("Give at least one tag name.")
        if (wanted.size > MAX_TAGS) return Outcome.Failed("Add at most $MAX_TAGS tags at a time.")

        val manager = when (val opened = openEditor(documentId)) {
            is Opened.Editor -> opened.manager
            is Opened.Failure -> return opened.outcome
        }
        if (manager.documentInfo.value.isLocked) {
            return Outcome.Failed("The document is locked in Writeopia. Unlock it to write in it.")
        }

        val added = mutableListOf<Pair<String, Boolean>>()
        val already = mutableListOf<String>()
        for (name in wanted) {
            if (bridge.isStopped(ticket)) return Outcome.Stopped
            val (tag, created) = tags.findOrCreate(name)
            if (tag.id in manager.documentTagIds.value) {
                already.add(tag.name)
            } else {
                manager.addDocumentTag(tag.id)
                added.add(tag.name to created)
            }
        }

        lastSeen = snapshot(manager)
        Outcome.Tagged(manager.documentInfo.value.id, titleOf(manager), added, already)
    }

    private sealed interface Opened {
        data class Editor(val manager: WriteopiaStateManager) : Opened

        data class Failure(val outcome: Outcome) : Opened
    }

    /** The editor of [documentId], opened when it isn't on screen; the one on screen when null. */
    private suspend fun openEditor(documentId: String?): Opened {
        val current = bridge.current?.takeIf { it.isInitialized() }

        val manager = when {
            documentId.isNullOrBlank() ->
                current
                    ?: return Opened.Failure(
                        Outcome.Failed(
                            "No document is open in Writeopia. Pass the document_id that create_document returned, or create a document."
                        )
                    )

            current?.documentInfo?.value?.id == documentId -> current

            else -> {
                bridge.requestOpen(ClaudeCodeBridge.OpenRequest.Document(documentId))
                awaitEditor { manager -> manager.documentInfo.value.id == documentId }
                    ?: return Opened.Failure(
                        Outcome.Failed("Writeopia could not open a document with the id $documentId.")
                    )
            }
        }

        // A document just opened may still be merged with its copy in the cloud, which would
        // replace what is being written: wait for it, and don't write while it lasts.
        withTimeoutOrNull(openTimeout) { isLoading.first { loading -> !loading } }
            ?: return Opened.Failure(
                Outcome.Failed("Writeopia is still loading the document. Try again in a moment.")
            )
        return Opened.Editor(manager)
    }

    /**
     * Makes a folder named [name] inside [parentId] (the folder the person has open when null) and
     * shows it, so the person sees it appear.
     */
    suspend fun createFolder(name: String, parentId: String?): Outcome = inAiMode {
        val folders = folders ?: return Outcome.Failed("This Writeopia app can't make folders.")
        val parent = parentId?.trim()?.takeIf { it.isNotEmpty() } ?: folders.currentFolderId()
        if (!folders.exists(parent)) return noFolder(parent)

        val folderId = folders.create(name.trim(), parent)
        bridge.requestOpen(ClaudeCodeBridge.OpenRequest.Folder(folderId))
        Outcome.FolderCreated(folderId, name.trim(), parent)
    }

    /**
     * Claude Code is done: AI mode ends and the person can edit again. Waits for a call still
     * writing, so it never ends AI mode in the middle of one.
     */
    suspend fun finish(): Outcome = mutex.withLock {
        bridge.finish()
        Outcome.Finished
    }

    /** The folders, to choose where to write. Only reads: no AI mode, no banner. */
    suspend fun listFolders(): Outcome {
        val folders = folders ?: return Outcome.Failed("This Writeopia app can't list folders.")
        return Outcome.Folders(folders.list(), folders.currentFolderId())
    }

    private fun noFolder(id: String) = Outcome.Failed(
        "Writeopia has no folder with the id $id. Use the folder_id that create_folder returned, or leave it out."
    )

    /**
     * Runs [block] in AI mode, one call at a time. Refused right away when the person stopped
     * Claude Code a moment ago.
     */
    private suspend inline fun inAiMode(block: (ticket: Long) -> Outcome): Outcome = mutex.withLock {
        val ticket = bridge.beginWriting() ?: return Outcome.Stopped
        try {
            val outcome = block(ticket)
            if (bridge.isStopped(ticket)) Outcome.Stopped else outcome
        } finally {
            bridge.endWriting(ticket)
        }
    }

    private suspend fun write(manager: WriteopiaStateManager, title: String?, markdown: String, ticket: Long): Outcome {
        if (bridge.isStopped(ticket)) return Outcome.Stopped
        if (manager.documentInfo.value.isLocked) {
            return Outcome.Failed("The document is locked in Writeopia. Unlock it to write in it.")
        }

        val blocks = LiveMarkdown.blocks(markdown)
        val done = writer.write(manager, title, blocks) { bridge.isStopped(ticket) }
        lastSeen = snapshot(manager)
        if (!done) return Outcome.Stopped

        return Outcome.Written(
            documentId = manager.documentInfo.value.id,
            title = titleOf(manager),
            blocks = blocks.size,
        )
    }

    private fun titleOf(manager: WriteopiaStateManager): String =
        manager.currentStory.value.stories.values
            .firstOrNull { step -> step.type == StoryTypes.TITLE.type }
            ?.text
            ?.takeIf { it.isNotBlank() }
            ?: manager.documentInfo.value.title

    /** The editor on screen once it shows a document that passes [accept], or null after the timeout. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun awaitEditor(accept: (WriteopiaStateManager) -> Boolean): WriteopiaStateManager? =
        withTimeoutOrNull(openTimeout) {
            bridge.editors
                .flatMapLatest { editors ->
                    val last = editors.lastOrNull()
                    // The document info changes when the editor loads or creates its document.
                    last?.documentInfo?.map { last } ?: flowOf(null)
                }
                .first { manager -> manager != null && manager.isInitialized() && accept(manager) }
        }

    private companion object {
        const val MAX_TAGS = 10
    }
}
