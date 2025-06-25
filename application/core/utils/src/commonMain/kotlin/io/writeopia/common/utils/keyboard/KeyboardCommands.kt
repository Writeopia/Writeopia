package io.writeopia.common.utils.keyboard

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

object KeyboardCommands {
    fun isUndoKeyboardEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            !keyEvent.isShiftPressed &&
            keyEvent.key.keyCode == Key.Z.keyCode &&
            keyEvent.type == KeyEventType.KeyDown

    fun isRedoKeyboardEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            keyEvent.isShiftPressed &&
            keyEvent.key.keyCode == Key.Z.keyCode &&
            keyEvent.type == KeyEventType.KeyDown

    fun isDeleteEvent(keyEvent: KeyEvent) =
        keyEvent.key.keyCode == Key.Delete.keyCode ||
            keyEvent.key.keyCode == Key.Backspace.keyCode

    fun isSelectAllEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            keyEvent.key.keyCode == Key.A.keyCode &&
            keyEvent.type == KeyEventType.KeyUp

    fun isBoldEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            keyEvent.key.keyCode == Key.B.keyCode &&
            keyEvent.type == KeyEventType.KeyUp

    fun isItalicEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            keyEvent.key.keyCode == Key.I.keyCode &&
            keyEvent.type == KeyEventType.KeyUp

    fun isUnderlineEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            keyEvent.key.keyCode == Key.U.keyCode &&
            keyEvent.type == KeyEventType.KeyUp

    fun isLinkEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            keyEvent.key.keyCode == Key.L.keyCode &&
            keyEvent.type == KeyEventType.KeyUp

    fun isLocalSaveEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            keyEvent.isShiftPressed &&
            keyEvent.key.keyCode == Key.S.keyCode &&
            keyEvent.type == KeyEventType.KeyUp

    fun isBoxEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            keyEvent.isShiftPressed &&
            keyEvent.key.keyCode == Key.B.keyCode &&
            keyEvent.type == KeyEventType.KeyUp

    fun isCopyEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            keyEvent.key.keyCode == Key.C.keyCode &&
            keyEvent.type == KeyEventType.KeyUp

    fun isCutEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            keyEvent.key.keyCode == Key.X.keyCode &&
            keyEvent.type == KeyEventType.KeyUp

    fun isQuestionEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            keyEvent.key.keyCode == Key.K.keyCode &&
            keyEvent.type == KeyEventType.KeyUp

    fun isCancelEvent(keyEvent: KeyEvent) =
        keyEvent.key.keyCode == Key.Escape.keyCode &&
            keyEvent.type == KeyEventType.KeyUp

    fun isAcceptAiEvent(keyEvent: KeyEvent) =
        keyEvent.key.keyCode == Key.Tab.keyCode &&
            keyEvent.type == KeyEventType.KeyUp
}
