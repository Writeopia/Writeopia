package io.writeopia.editor.features.summarization

expect class SummarizationService() {
    suspend fun summarize(text: String): Result<String>
    fun close()
}
