package io.writeopia.notemenu.ui.screen.menu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.writeopia.ai.task.AiTaskManager

/** Nothing in the screen: the Android app follows the AI tasks with a notification. */
@Composable
actual fun MobileAiTasksIndicator(aiTaskManager: AiTaskManager, modifier: Modifier) = Unit
