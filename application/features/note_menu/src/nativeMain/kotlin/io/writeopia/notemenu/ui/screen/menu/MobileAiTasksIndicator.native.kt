package io.writeopia.notemenu.ui.screen.menu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.writeopia.ai.task.AiTaskManager
import io.writeopia.ai.task.ui.AiTaskIndicator

@Composable
actual fun MobileAiTasksIndicator(aiTaskManager: AiTaskManager, modifier: Modifier) {
    AiTaskIndicator(
        tasksFlow = aiTaskManager.tasks,
        onClearFinished = aiTaskManager::clearFinishedTasks,
        onCancelTask = aiTaskManager::cancelTask,
        modifier = modifier
    )
}
