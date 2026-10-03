package io.writeopia.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.writeopia.ai.task.AiTask
import io.writeopia.ai.task.AiTaskManager
import io.writeopia.ai.task.AiTaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Follows the AI tasks of the app with a notification each, instead of the floating indicator of
 * the desktop. A running task shows its progress and a Cancel action; a finished one stays until
 * the user swipes it away. Lives for the whole process, like the tasks themselves.
 *
 * @param openApp the intent of a tap on a notification; the launcher activity by default.
 */
class AiTaskNotifier(
    private val context: Context,
    private val aiTaskManager: AiTaskManager = AiTaskManager.singleton(),
    private val openApp: () -> Intent? = {
        context.packageManager.getLaunchIntentForPackage(context.packageName)
    },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** What was last shown for each task, so an unchanged task doesn't re-notify. */
    private val shown = mutableMapOf<String, Pair<AiTaskStatus, Float?>>()

    fun start() {
        createChannel()

        scope.launch {
            aiTaskManager.tasks.collect { tasks ->
                tasks.forEach(::show)
                // Tasks leave the list a while after they finish; forget them too.
                shown.keys.retainAll(tasks.mapTo(mutableSetOf()) { task -> task.id })
            }
        }
    }

    private fun show(task: AiTask) {
        val state = task.status to task.progress
        if (shown[task.id] == state) return
        shown[task.id] = state

        val manager = NotificationManagerCompat.from(context)
        val notificationId = task.id.hashCode()

        if (task.status == AiTaskStatus.CANCELLED) {
            manager.cancel(notificationId)
            return
        }

        if (!canNotify()) return

        manager.notify(notificationId, buildNotification(task))
    }

    private fun buildNotification(task: AiTask): android.app.Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_ai)
            .setContentTitle(task.description)
            .setContentIntent(openAppIntent())
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        when (task.status) {
            AiTaskStatus.QUEUED, AiTaskStatus.RUNNING -> {
                val text = if (task.status == AiTaskStatus.QUEUED) {
                    R.string.ai_task_queued
                } else {
                    R.string.ai_task_running
                }
                val progress = task.progress

                builder
                    .setContentText(context.getString(text))
                    .setOngoing(true)
                    .addAction(
                        0,
                        context.getString(R.string.ai_task_cancel),
                        cancelIntent(task.id)
                    )

                if (progress == null) {
                    builder.setProgress(0, 0, true)
                } else {
                    builder.setProgress(PROGRESS_MAX, (progress * PROGRESS_MAX).toInt(), false)
                }
            }

            AiTaskStatus.COMPLETED -> {
                builder
                    .setContentText(context.getString(R.string.ai_task_done))
                    .setAutoCancel(true)
            }

            AiTaskStatus.FAILED -> {
                val reason = task.errorMessage?.takeIf { it.isNotBlank() }
                val text = context.getString(R.string.ai_task_failed)

                builder
                    .setContentText(if (reason != null) "$text: $reason" else text)
                    .setStyle(NotificationCompat.BigTextStyle())
                    .setAutoCancel(true)
            }

            AiTaskStatus.CANCELLED -> Unit
        }

        return builder.build()
    }

    private fun openAppIntent(): PendingIntent? {
        val intent = openApp()
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return null

        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun cancelIntent(taskId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            CancelAiTaskReceiver.intent(context, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.ai_tasks_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.ai_tasks_channel_description)
        }

        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "ai_tasks"
        private const val PROGRESS_MAX = 100
    }
}
