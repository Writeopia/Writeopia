package io.writeopia.editor.features.summarization

actual class SummarizationService actual constructor() {

    actual suspend fun summarize(text: String): Result<String> {
        return Result.failure(
            UnsupportedOperationException("ML Kit Summarization is not available on web platforms")
        )
    }

    actual fun close() {
        // No-op on web
    }
}
