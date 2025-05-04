package io.writeopia.ui.draganddrop.target.external

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import io.writeopia.sdk.models.files.ExternalFile

@Composable
fun externalImageDropTarget(
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onEnter: () -> Unit,
    onExit: () -> Unit,
    onFileReceived: (List<ExternalFile>) -> Unit,
) = remember {
    object : DragAndDropTarget {
        override fun onStarted(event: DragAndDropEvent) {
            super.onStarted(event)
            onStart()
        }

        override fun onEnded(event: DragAndDropEvent) {
            super.onEnded(event)
            onEnd()
        }

        override fun onEntered(event: DragAndDropEvent) {
            super.onEntered(event)
            onEnter()
        }

        override fun onExited(event: DragAndDropEvent) {
            super.onExited(event)
            onExit()
        }

        override fun onDrop(event: DragAndDropEvent): Boolean =
            handleImageDrop(event, onFileReceived)
    }
}
