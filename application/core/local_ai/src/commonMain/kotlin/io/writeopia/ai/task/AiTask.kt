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

data class AiTask(
    val id: String,
    val type: AiTaskType,
    val description: String,
    val status: AiTaskStatus,
    val errorMessage: String? = null,
    val progress: Float? = null // Progress percentage (0.0 to 1.0) for tasks that support it
)
