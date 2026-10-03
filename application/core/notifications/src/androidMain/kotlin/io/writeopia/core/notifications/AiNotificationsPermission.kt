package io.writeopia.core.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.writeopia.ai.task.AiTaskManager
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

private const val NOTIFICATIONS_REQUEST_CODE = 4101

/**
 * Asks for the notifications permission the first time an AI task starts, since that's when the
 * progress notification is about to appear. Nothing happens before Android 13, which needs none.
 */
@Composable
fun RequestAiNotificationsPermission(aiTaskManager: AiTaskManager = AiTaskManager.singleton()) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        aiTaskManager.tasks.filter { tasks -> tasks.isNotEmpty() }.first()

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        val activity = context.findActivity()

        if (!granted && activity != null) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATIONS_REQUEST_CODE
            )
        }
    }
}

private fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
