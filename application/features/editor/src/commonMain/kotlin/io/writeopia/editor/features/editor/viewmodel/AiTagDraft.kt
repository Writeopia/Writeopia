package io.writeopia.editor.features.editor.viewmodel

/** What the "suggest with AI" button of the add-tag dialog is doing. */
sealed class AiTagDraft {
    /** Nothing asked. */
    data object Idle : AiTagDraft()

    /** The cloud AI is making up a tag. */
    data object Loading : AiTagDraft()

    /** The tag the AI proposed, for the user to edit and confirm. */
    data class Ready(val name: String, val description: String) : AiTagDraft()

    /** The AI answered nothing usable, or the request failed. */
    data object Failed : AiTagDraft()
}
