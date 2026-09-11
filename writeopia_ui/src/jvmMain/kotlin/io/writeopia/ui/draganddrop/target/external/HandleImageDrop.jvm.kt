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
    println("[PDF/IMAGE DROP] handleImageDrop called")
    val files = event.awtTransferable.let { transferable ->
        println("[PDF/IMAGE DROP] DataFlavor supported: ${transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)}")
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            val files =
                transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>

            println("[PDF/IMAGE DROP] Files received: ${files.size}")
            files.forEach { file ->
                println("[PDF/IMAGE DROP] File: ${file.name}, Extension: '${file.extension}', Path: ${file.absolutePath}")
            }

            if (files.isNotEmpty()) {
                val externalFiles = files.map { ExternalFile(it.absolutePath, it.extension, it.name) }
                println("[PDF/IMAGE DROP] Calling receiveFiles with ${externalFiles.size} files")
                receiveFiles(externalFiles)
            }

            files
        } else {
            println("[PDF/IMAGE DROP] No files found in drop event")
            emptyList()
        }
    }

    val result = files.isNotEmpty()
    println("[PDF/IMAGE DROP] handleImageDrop returning: $result")
    return result
}

@OptIn(ExperimentalComposeUiApi::class)
actual fun shouldAcceptImageDrop(event: DragAndDropEvent): Boolean {
    println("[PDF/IMAGE DROP] shouldAcceptImageDrop called")
    val transferable = event.awtTransferable
    val supported = transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
    println("[PDF/IMAGE DROP] shouldAcceptImageDrop returning: $supported")

    if (!supported) {
        println("[PDF/IMAGE DROP] Available flavors: ${transferable.transferDataFlavors.joinToString { it.toString() }}")
    }

    return supported
}
