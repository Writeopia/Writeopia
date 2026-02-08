package io.writeopia.sdk.models.story

data class TagInfo(val tag: Tag, val position: Int = 0) {
    companion object {
        fun fromString(stringTag: String): TagInfo? = Tag.fromString(stringTag)?.let(::TagInfo)
    }
}

enum class Tag(val label: String) {
    H1("H1"),
    H2("H2"),
    H3("H3"),
    H4("H4"),
    HIGH_LIGHT_BLOCK("HIGH_LIGHT_BLOCK"),
    CODE_BLOCK("CODE_BLOCK"),
    HIDDEN_HX("HIGH_LIGHT_BLOCK"),
    COLLAPSED("COLLAPSED"),
    AI_SUGGESTION("AI_SUGGESTION"),
    FIRST_AI_SUGGESTION("FIRST_AI_SUGGESTION");

    fun isTitle() =
        when (this) {
            H1, H2, H3, H4 -> true
            else -> false
        }

    fun hasPosition() = when (this) {
        H1, H2, H3, H4, HIDDEN_HX, COLLAPSED, AI_SUGGESTION, FIRST_AI_SUGGESTION, CODE_BLOCK -> false
        HIGH_LIGHT_BLOCK -> true
    }

    fun isErasable() = when (this) {
        HIDDEN_HX, COLLAPSED, AI_SUGGESTION, FIRST_AI_SUGGESTION, CODE_BLOCK -> false
        H1, H2, H3, H4, HIGH_LIGHT_BLOCK -> true
    }

    fun mustCarryOver() = when (this) {
        H1, H2, H3, H4, HIDDEN_HX, COLLAPSED, AI_SUGGESTION, FIRST_AI_SUGGESTION, CODE_BLOCK -> false
        HIGH_LIGHT_BLOCK -> true
    }

    fun isHidden() = when (this) {
        HIDDEN_HX -> true
        else -> false
    }

    companion object {
        fun titleTags(): Set<Tag> = setOf(H1, H2, H3, H4)

        fun fromString(fromTag: String): Tag? = entries.firstOrNull { it.label == fromTag }
    }
}
