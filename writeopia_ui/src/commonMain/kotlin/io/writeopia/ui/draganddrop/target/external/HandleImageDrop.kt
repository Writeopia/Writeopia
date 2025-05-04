package io.writeopia.ui.draganddrop.target.external

import androidx.compose.ui.draganddrop.DragAndDropEvent
import io.writeopia.sdk.models.files.ExternalFile

expect fun handleImageDrop(
    event: DragAndDropEvent,
    receiveFiles: (List<ExternalFile>) -> Unit
): Boolean

expect fun shouldAcceptImageDrop(event: DragAndDropEvent): Boolean

