package io.writeopia.langchain4k

data class LcDocument(
    val id: String?,
    val page_content: String,
    val metadata: Map<String, String>
)
