package io.writeopia.notemenu.ui.screen.menu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.writeopia.ai.task.AiTaskManager

/**
 * Shows the progress of the AI tasks on the mobile notes menu, like the summaries that run in the
 * background after the selection is cleared. Android reports them with a notification instead,
 * which also lets the user cancel them from outside the app, so it renders nothing here.
 */
@Composable
expect fun MobileAiTasksIndicator(aiTaskManager: AiTaskManager, modifier: Modifier = Modifier)
