package io.writeopia.ui.draganddrop.target.external

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.awtTransferable
import io.writeopia.sdk.models.files.ExternalFile
import java.awt.datatransfer.DataFlavor
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
actual fun handleImageDrop(
    event: DragAndDropEvent,
    receiveFiles: (List<ExternalFile>) -> Unit
): Boolean {
    val files = event.awtTransferable.let { transferable ->
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            val files =
                transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>

            if (files.isNotEmpty()) {
                receiveFiles(files.map { ExternalFile(it.absolutePath, it.extension, it.name) })
            }

            files
        } else {
            emptyList()
        }
    }

    return files.isNotEmpty()
}

@OptIn(ExperimentalComposeUiApi::class)
actual fun shouldAcceptImageDrop(event: DragAndDropEvent): Boolean {
    val transferable = event.awtTransferable

    return transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
}
