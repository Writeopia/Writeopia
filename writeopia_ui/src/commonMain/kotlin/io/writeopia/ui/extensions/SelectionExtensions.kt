package io.writeopia.ui.extensions

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import io.writeopia.sdk.model.story.Selection
import kotlin.math.min

fun Selection.toTextRange(text: String) =
    TextRange(start = min(start, text.length), end = min(end, text.length))

fun Selection.toTextRange(text: String, textLayoutResult: TextLayoutResult?): TextRange {
    return when {
        fromEnd && textLayoutResult != null && textLayoutResult.lineCount > 0 -> {
            val lastLineIndex = textLayoutResult.lineCount - 1
            val lastLineStart = textLayoutResult.getLineStart(lastLineIndex)
            val lastLineEnd = textLayoutResult.getLineEnd(lastLineIndex)
            val lastLineLength = lastLineEnd - lastLineStart
            val cursorPos = lastLineStart + min(start, lastLineLength)
            TextRange(cursorPos, cursorPos)
        }

        // Fallback when no layout yet: position at end, will be corrected later
        fromEnd -> TextRange(text.length, text.length)
        
        else -> TextRange(start = min(start, text.length), end = min(end, text.length))
    }
}
