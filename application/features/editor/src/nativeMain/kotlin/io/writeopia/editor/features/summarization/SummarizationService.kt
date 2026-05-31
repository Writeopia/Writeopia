package io.writeopia.editor.features.summarization

actual class SummarizationService {

    actual suspend fun summarize(text: String): Result<String> {
        return Result.failure(
            UnsupportedOperationException("ML Kit Summarization is not available on iOS")
        )
    }

    actual fun close() {
        // No-op on iOS
    }
}
