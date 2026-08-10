package io.writeopia.ai.task

enum class AiTaskType {
    SUMMARIZATION
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
    val errorMessage: String? = null
)
