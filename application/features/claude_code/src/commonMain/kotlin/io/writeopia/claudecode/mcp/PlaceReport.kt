package io.writeopia.claudecode.mcp

/** The document open in the editor, as get_current_place reports it. */
data class OpenDocument(
    val id: String,
    val title: String,
    /** The folders from the top level down to the one holding the document; empty at the top level. */
    val folderPath: List<LiveFolder>,
    val tags: List<String>,
    val isLocked: Boolean,
    val hasHeaderPicture: Boolean,
)

/** What changed since Claude Code's last call, as get_current_place reports it. */
sealed interface PlaceChange {
    /** The person moved away from the document Claude last worked in. */
    data class Moved(val fromId: String, val fromTitle: String) : PlaceChange

    /** The person edited the document Claude last worked in. */
    data class Edited(val added: Int, val changed: Int, val removed: Int) : PlaceChange

    data object Unchanged : PlaceChange
}

/**
 * What a document looked like at the end of a call of Claude Code: its id, title and blocks (id
 * to content), to tell later what the person did meanwhile.
 */
data class DocumentSnapshot(val documentId: String, val title: String, val blocks: Map<String, String>) {

    /** What the person did between this snapshot and [now] (the document open now, if any). */
    fun changeTo(now: DocumentSnapshot?): PlaceChange {
        if (now == null || now.documentId != documentId) return PlaceChange.Moved(documentId, title)

        val added = now.blocks.keys.count { id -> id !in blocks }
        val removed = blocks.keys.count { id -> id !in now.blocks }
        val changed = now.blocks.count { (id, content) -> blocks[id] != null && blocks[id] != content }
        return if (added + removed + changed == 0) PlaceChange.Unchanged else PlaceChange.Edited(added, changed, removed)
    }
}

/**
 * The answer of get_current_place. Mirrored word for word by `PlaceReport.swift`, the Markdown of
 * the content aside (each app writes its own).
 */
object PlaceReport {
    const val CONTENT_LIMIT = 20_000

    fun describe(
        document: OpenDocument?,
        currentFolder: LiveFolder?,
        change: PlaceChange?,
        content: String?,
    ): String = buildString {
        if (document == null) {
            append("No document is open in Writeopia. ")
            append(
                if (currentFolder == null) {
                    "The app has the top level open."
                } else {
                    "The app has \"${currentFolder.name}\" (folder_id: ${currentFolder.id}) open."
                }
            )
        } else {
            append("Writeopia shows the document \"${document.title}\" (document_id: ${document.id}) in ")
            append(
                if (document.folderPath.isEmpty()) {
                    "the top level."
                } else {
                    document.folderPath.joinToString(" / ") { it.name } +
                        " (folder_id: ${document.folderPath.last().id})."
                }
            )
            append("\nTags: ${document.tags.joinToString(", ").ifEmpty { "none" }}.")
            append(" Locked: ${yesNo(document.isLocked)}. Header picture: ${yesNo(document.hasHeaderPicture)}.")
        }

        when (change) {
            null -> {}
            is PlaceChange.Moved -> append(
                "\n\nSince your last call, the person " +
                    (if (document == null) "left" else "moved here from") +
                    " \"${change.fromTitle}\" (document_id: ${change.fromId})."
            )
            is PlaceChange.Edited -> append(
                "\n\nSince your last call, the person changed this document: ${change.added} blocks added, " +
                    "${change.changed} changed, ${change.removed} removed."
            )
            PlaceChange.Unchanged -> append("\n\nSince your last call, nothing changed here.")
        }

        if (document != null && content != null) {
            val text = content.trim()
            when {
                text.isEmpty() -> append("\n\nContent: empty.")
                text.length <= CONTENT_LIMIT -> append("\n\nContent (Markdown):\n$text")
                else -> append(
                    "\n\nContent (Markdown):\n${text.take(CONTENT_LIMIT)}\n…(cut: ${text.length - CONTENT_LIMIT} more characters)"
                )
            }
        }
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"
}
