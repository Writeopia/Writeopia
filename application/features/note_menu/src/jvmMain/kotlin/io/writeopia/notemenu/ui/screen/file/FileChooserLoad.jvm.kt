package io.writeopia.notemenu.ui.screen.file

import androidx.compose.ui.awt.ComposeWindow
import io.writeopia.sdk.models.files.ExternalFile
import java.awt.FileDialog

actual fun fileChooserLoad(title: String): List<ExternalFile> {
    val dialog = FileDialog(ComposeWindow(), title, FileDialog.LOAD).apply {
        isMultipleMode = true
        isVisible = true
    }

    return dialog.files
        ?.takeIf { it.isNotEmpty() }
        ?.map {
            ExternalFile(
                fullPath = it.absolutePath,
                extension = it.extension,
                name = it.name
            )
        }
        ?: emptyList()
}
