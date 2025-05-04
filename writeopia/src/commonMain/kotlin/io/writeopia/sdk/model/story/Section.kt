package io.writeopia.sdk.model.story

data class Section(
    val title: String,
    val content: List<String>
) {

    fun asText(): String =
        buildString {
            appendLine(title)
            content.forEach(this::appendLine)
        }
}

