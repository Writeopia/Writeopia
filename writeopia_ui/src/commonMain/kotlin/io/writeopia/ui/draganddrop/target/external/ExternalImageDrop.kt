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
    println("[PDF/IMAGE DROP] externalImageDropTarget composable created/remembered")
    object : DragAndDropTarget {
        override fun onStarted(event: DragAndDropEvent) {
            println("[PDF/IMAGE DROP] DragAndDropTarget.onStarted")
            super.onStarted(event)
            onStart()
        }

        override fun onEnded(event: DragAndDropEvent) {
            println("[PDF/IMAGE DROP] DragAndDropTarget.onEnded")
            super.onEnded(event)
            onEnd()
        }

        override fun onEntered(event: DragAndDropEvent) {
            println("[PDF/IMAGE DROP] DragAndDropTarget.onEntered")
            super.onEntered(event)
            onEnter()
        }

        override fun onExited(event: DragAndDropEvent) {
            println("[PDF/IMAGE DROP] DragAndDropTarget.onExited")
            super.onExited(event)
            onExit()
        }

        override fun onDrop(event: DragAndDropEvent): Boolean {
            println("[PDF/IMAGE DROP] DragAndDropTarget.onDrop called")
            return handleImageDrop(event, onFileReceived)
        }
    }
}
