package io.writeopia.ai.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A global AI task manager that queues and executes AI tasks sequentially.
 *
 * This manager uses a Mutex to ensure only one AI task runs at a time,
 * preventing overload on AI services like LocalAi.
 *
 * Usage:
 * 1. Call [enqueueTask] to add a task to the queue
 * 2. Observe [tasks] StateFlow for UI updates
 * 3. Tasks auto-remove after completion/failure with a delay
 */
class AiTaskManager(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val autoRemoveDelayMs: Long = 5000L
) {
    private val _tasks = MutableStateFlow<List<AiTask>>(emptyList())
    val tasks: StateFlow<List<AiTask>> = _tasks.asStateFlow()

    private val executionMutex = Mutex()
    private val activeJobs = mutableMapOf<String, Job>()

    /**
     * Enqueues a new AI task for execution.
     *
     * Tasks are executed sequentially using a Mutex. The task status will be
     * updated automatically as it progresses through QUEUED -> RUNNING -> COMPLETED/FAILED.
     *
     * @param id Unique identifier for the task
     * @param type The type of AI task
     * @param description Human-readable description of the task
     * @param execution Suspend function that performs the actual work
     */
    fun enqueueTask(
        id: String,
        type: AiTaskType,
        description: String,
        execution: suspend () -> Result<Unit>
    ) {
        val task = AiTask(
            id = id,
            type = type,
            description = description,
            status = AiTaskStatus.QUEUED
        )

        _tasks.update { currentTasks -> currentTasks + task }

        val job = scope.launch(dispatcher) {
            try {
                executionMutex.withLock {
                    // Check if cancelled while waiting in queue
                    if (isCancelled(id)) {
                        activeJobs.remove(id)
                        return@withLock
                    }

                    updateTaskStatus(id, AiTaskStatus.RUNNING)

                    val result = runCatching { execution() }.getOrElse { Result.failure(it) }

                    if (result.isSuccess) {
                        updateTaskStatus(id, AiTaskStatus.COMPLETED)
                    } else {
                        val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                        updateTaskStatus(id, AiTaskStatus.FAILED, errorMessage)
                    }

                    delay(autoRemoveDelayMs)
                    removeTask(id)
                }
            } catch (e: CancellationException) {
                // Task was cancelled, status already updated by cancelTask
                throw e
            } finally {
                activeJobs.remove(id)
            }
        }

        activeJobs[id] = job
    }

    /**
     * Cancels a task by its ID.
     *
     * If the task is queued, it will be marked as cancelled and removed.
     * If the task is running, the execution will be interrupted.
     *
     * @param taskId The ID of the task to cancel
     */
    fun cancelTask(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return

        if (task.status == AiTaskStatus.QUEUED || task.status == AiTaskStatus.RUNNING) {
            updateTaskStatus(taskId, AiTaskStatus.CANCELLED)
            activeJobs[taskId]?.cancel()

            scope.launch(dispatcher) {
                delay(autoRemoveDelayMs)
                removeTask(taskId)
            }
        }
    }

    /**
     * Cancels all tasks whose IDs start with the given prefix.
     *
     * Useful for cancelling all tasks related to a specific document or context.
     *
     * @param prefix The prefix to match task IDs against
     */
    fun cancelTasksByPrefix(prefix: String) {
        _tasks.value
            .filter { it.id.startsWith(prefix) && (it.status == AiTaskStatus.QUEUED || it.status == AiTaskStatus.RUNNING) }
            .forEach { cancelTask(it.id) }
    }

    /**
     * Checks if a task has been cancelled.
     */
    private fun isCancelled(taskId: String): Boolean =
        _tasks.value.find { it.id == taskId }?.status == AiTaskStatus.CANCELLED

    /**
     * Updates the status of a task by its ID.
     */
    private fun updateTaskStatus(
        taskId: String,
        status: AiTaskStatus,
        errorMessage: String? = null
    ) {
        _tasks.update { currentTasks ->
            currentTasks.map { task ->
                if (task.id == taskId) {
                    task.copy(status = status, errorMessage = errorMessage)
                } else {
                    task
                }
            }
        }
    }

    /**
     * Removes a task from the list by its ID.
     */
    private fun removeTask(taskId: String) {
        _tasks.update { currentTasks ->
            currentTasks.filter { it.id != taskId }
        }
    }

    /**
     * Clears all completed, failed, and cancelled tasks immediately.
     */
    fun clearFinishedTasks() {
        _tasks.update { currentTasks ->
            currentTasks.filter { task ->
                task.status == AiTaskStatus.QUEUED || task.status == AiTaskStatus.RUNNING
            }
        }
    }

    /**
     * Returns true if there are any active (queued or running) tasks.
     */
    fun hasActiveTasks(): Boolean =
        _tasks.value.any { task ->
            task.status == AiTaskStatus.QUEUED || task.status == AiTaskStatus.RUNNING
        }

    /**
     * Returns the count of active tasks.
     */
    fun activeTaskCount(): Int =
        _tasks.value.count { task ->
            task.status == AiTaskStatus.QUEUED || task.status == AiTaskStatus.RUNNING
        }

    companion object {
        private var instance: AiTaskManager? = null

        /**
         * Gets the singleton instance of AiTaskManager.
         * Creates one if it doesn't exist.
         */
        fun singleton(): AiTaskManager = instance ?: AiTaskManager().also { instance = it }

        /**
         * Initializes the singleton with a custom instance.
         * Useful for testing or custom configuration.
         */
        fun initialize(manager: AiTaskManager) {
            instance = manager
        }
    }
}
