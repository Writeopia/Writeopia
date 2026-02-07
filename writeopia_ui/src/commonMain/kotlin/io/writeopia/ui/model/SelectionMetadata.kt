package io.writeopia.ui.model

import io.writeopia.sdk.models.story.StoryTypes

enum class SelectionMetadata {
    BOLD,
    ITALIC,
    UNDERLINE,
    TITLE,
    SUBTITLE,
    HEADING,
    BOX,
    CHECK_ITEM,
    CODE_BLOCK,
    UNORDERED_LIST_ITEM,
    DOCUMENT_LINK;

    companion object {
        fun fromStoryType(storyTypeNumer: Int): SelectionMetadata? {
            val storyType = StoryTypes.fromNumber(storyTypeNumer)

            return when (storyType) {
                StoryTypes.CHECK_ITEM -> CHECK_ITEM
                StoryTypes.UNORDERED_LIST_ITEM -> UNORDERED_LIST_ITEM
                StoryTypes.DOCUMENT_LINK -> DOCUMENT_LINK
                StoryTypes.CODE_BLOCK -> CODE_BLOCK
                else -> null
            }
        }
    }
}
