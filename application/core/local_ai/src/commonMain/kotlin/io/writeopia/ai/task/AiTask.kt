package io.writeopia.ai.task

enum class AiTaskType {
    SUMMARIZATION,
    TEXT_GENERATION,
    PRESENTATION,
    MODEL_DOWNLOAD,

    /** The description of the page of a link card, written by the backend AI. */
    LINK_DESCRIPTION
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
    // Optional custom error message
    val errorMessage: String? = null,
    // Progress percentage (0.0 to 1.0) for tasks that support it
    val progress: Float? = null
)
