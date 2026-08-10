package io.writeopia.ai.task.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.writeopia.ai.task.AiTask
import io.writeopia.ai.task.AiTaskManager
import io.writeopia.ai.task.AiTaskStatus
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.ui.icons.WrSdkIcons
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AiTaskIndicator(
    tasksFlow: StateFlow<List<AiTask>>,
    onClearFinished: () -> Unit,
    onCancelTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by tasksFlow.collectAsState()

    AnimatedVisibility(
        visible = tasks.isNotEmpty(),
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        TaskIndicatorContent(
            tasks = tasks,
            onClearFinished = onClearFinished,
            onCancelTask = onCancelTask
        )
    }
}

@Composable
private fun TaskIndicatorContent(
    tasks: List<AiTask>,
    onClearFinished: () -> Unit,
    onCancelTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val activeCount = tasks.count { it.status == AiTaskStatus.QUEUED || it.status == AiTaskStatus.RUNNING }
    val hasFinished = tasks.any {
        it.status == AiTaskStatus.COMPLETED ||
        it.status == AiTaskStatus.FAILED ||
        it.status == AiTaskStatus.CANCELLED
    }

    Surface(
        modifier = modifier
            .widthIn(min = 200.dp, max = 300.dp)
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header - always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (activeCount > 0) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = WrIcons.ai,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (activeCount > 0) {
                            "$activeCount task${if (activeCount > 1) "s" else ""} running"
                        } else {
                            "AI Tasks"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = if (expanded) WrIcons.smallArrowUp else WrIcons.smallArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    tasks.forEach { task ->
                        TaskItem(
                            task = task,
                            onCancel = { onCancelTask(task.id) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (hasFinished) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Clear finished",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { onClearFinished() }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskItem(
    task: AiTask,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCancellable = task.status == AiTaskStatus.QUEUED || task.status == AiTaskStatus.RUNNING

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TaskStatusIcon(status = task.status)

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (task.status == AiTaskStatus.FAILED && task.errorMessage != null) {
                Text(
                    text = task.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isCancellable) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = WrIcons.close,
                    contentDescription = "Cancel task",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TaskStatusIcon(
    status: AiTaskStatus,
    modifier: Modifier = Modifier
) {
    when (status) {
        AiTaskStatus.QUEUED -> {
            // Clock icon for queued
            val infiniteTransition = rememberInfiniteTransition(label = "queuedRotation")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation"
            )
            Icon(
                imageVector = WrIcons.sync,
                contentDescription = "Queued",
                modifier = modifier.size(14.dp).rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AiTaskStatus.RUNNING -> {
            CircularProgressIndicator(
                modifier = modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        AiTaskStatus.COMPLETED -> {
            Icon(
                imageVector = WrSdkIcons.check,
                contentDescription = "Completed",
                modifier = modifier.size(14.dp),
                tint = Color(0xFF4CAF50) // Green
            )
        }
        AiTaskStatus.FAILED -> {
            Icon(
                imageVector = WrIcons.close,
                contentDescription = "Failed",
                modifier = modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
        AiTaskStatus.CANCELLED -> {
            Icon(
                imageVector = WrIcons.close,
                contentDescription = "Cancelled",
                modifier = modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
