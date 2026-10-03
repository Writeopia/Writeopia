package io.writeopia.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.writeopia.ai.task.AiTaskManager

/** The Cancel action of an AI task notification. */
class CancelAiTaskReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CANCEL) return

        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        AiTaskManager.singleton().cancelTask(taskId)
    }

    companion object {
        const val ACTION_CANCEL = "io.writeopia.action.CANCEL_AI_TASK"
        const val EXTRA_TASK_ID = "task_id"

        fun intent(context: Context, taskId: String): Intent =
            Intent(context, CancelAiTaskReceiver::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_TASK_ID, taskId)
    }
}
