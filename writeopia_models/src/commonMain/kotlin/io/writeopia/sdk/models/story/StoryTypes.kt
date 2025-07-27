package io.writeopia.sdk.models.story

/**
 * The default types of [StoryTypes]. You can add more types without extending this enum (which
 * is final), by creating new strings of types.
 */
enum class StoryTypes(val type: StoryType) {
    TEXT(StoryType("message", 0)),
    TEXT_BOX(
        StoryType(
            "message_box",
            1
        )
    ),
    IMAGE(StoryType("image", 2)),
    GROUP_IMAGE(
        StoryType(
            "group_image",
            3
        )
    ),
    AUDIO(StoryType("audio", 4)),
    GROUP_VIDEO(
        StoryType(
            "group_video",
            5
        )
    ),
    VIDEO(StoryType("video", 6)),
    SPACE(StoryType("space", 7)),
    LAST_SPACE(
        StoryType(
            "last_space",
            8
        )
    ),
    ADD_BUTTON(StoryType("add_button", 9)),
    CHECK_ITEM(
        StoryType(
            "check_item",
            10
        )
    ),
    TITLE(StoryType("title", 11)),
    UNORDERED_LIST_ITEM(StoryType("unordered_list_item", 16)),
    CODE_BLOCK(
        StoryType(
            "code_block",
            1
        )
    ),
    ON_DRAG_SPACE(StoryType("on_drag_space", 17)),
    AI_ANSWER(StoryType("ai_answer", 18)),
    LOADING(StoryType("loading", 19)),
    DOCUMENT_LINK(StoryType("document_link", 20)),
    DIVIDER(StoryType("divider", 21)),
    EQUATION(StoryType("equation", 22)),
    ;

    companion object {
        fun fromName(stringValue: String): StoryTypes =
            entries.first { it.type.name == stringValue }

        fun fromNumber(number: Int): StoryTypes = entries.first { it.type.number == number }
    }
}
