package io.writeopia.ai.task

enum class AiTaskType {
    SUMMARIZATION,
    TEXT_GENERATION,
    MODEL_DOWNLOAD
}

enum class AiTaskStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class AiTaskErrorType {
    CANCELLED,
    UNKNOWN
}

data class AiTask(
    val id: String,
    val type: AiTaskType,
    val description: String,
    val status: AiTaskStatus,
    val errorType: AiTaskErrorType? = null,
    val errorMessage: String? = null, // Optional custom error message
    val progress: Float? = null // Progress percentage (0.0 to 1.0) for tasks that support it
)
