package io.writeopia.ui.platform

import java.awt.Desktop
import java.io.File

actual fun openFile(path: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) {
            val desktop = Desktop.getDesktop()

            if (desktop.isSupported(Desktop.Action.OPEN)) {
                desktop.open(File(path))
            }
        }
    }
}
