package io.writeopia.ui.draganddrop.target.external

import androidx.compose.ui.draganddrop.DragAndDropEvent
import io.writeopia.sdk.models.files.ExternalFile

actual fun handleImageDrop(
    event: DragAndDropEvent,
    receiveFiles: (List<ExternalFile>) -> Unit
): Boolean = false

actual fun shouldAcceptImageDrop(event: DragAndDropEvent): Boolean = false
