package io.writeopia.editor.features.editor.copy

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString

class CopyManager(private val clipboardManager: ClipboardManager) {

    fun copy(text: AnnotatedString) {
        clipboardManager.setText(text)
    }

    fun copyText(text: String) {
        clipboardManager.setText(AnnotatedString(text))
    }
}
