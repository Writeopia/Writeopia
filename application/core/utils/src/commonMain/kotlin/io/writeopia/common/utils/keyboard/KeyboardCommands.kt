package io.writeopia.common.utils.keyboard

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
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

    fun isSearchEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            keyEvent.key.keyCode == Key.F.keyCode &&
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
        keyEvent.key.keyCode == Key.Escape.keyCode

    // Not with Ctrl: Ctrl + Tab goes to the next tab.
    fun isAcceptAiEvent(keyEvent: KeyEvent) =
        keyEvent.key.keyCode == Key.Tab.keyCode &&
            !keyEvent.isCtrlPressed &&
            keyEvent.type == KeyEventType.KeyUp

    fun isEquationEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            keyEvent.key.keyCode == Key.E.keyCode &&
            keyEvent.type == KeyEventType.KeyUp

    // Cmd/Ctrl + Shift + -: Cmd/Ctrl + - alone makes the text smaller.
    fun isListEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            keyEvent.isShiftPressed &&
            keyEvent.key.keyCode == Key.Minus.keyCode &&
            keyEvent.type == KeyEventType.KeyUp

    // "+" shares its key with "=" on most layouts, so Cmd/Ctrl + = counts with or without Shift.
    // On key down, so holding the keys keeps zooming.
    fun isFontLargerEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            keyEvent.key.keyCode in fontLargerKeys &&
            keyEvent.type == KeyEventType.KeyDown

    fun isFontSmallerEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            !keyEvent.isShiftPressed &&
            keyEvent.key.keyCode in fontSmallerKeys &&
            keyEvent.type == KeyEventType.KeyDown

    fun isFontResetEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            keyEvent.key.keyCode in fontResetKeys &&
            keyEvent.type == KeyEventType.KeyDown

    private val fontLargerKeys = setOf(Key.Equals.keyCode, Key.Plus.keyCode, Key.NumPadAdd.keyCode, Key.NumPadEquals.keyCode)
    private val fontSmallerKeys = setOf(Key.Minus.keyCode, Key.NumPadSubtract.keyCode)
    private val fontResetKeys = setOf(Key.Zero.keyCode, Key.NumPad0.keyCode)

    // Tabs of the desktop app. On key down, so they act before a text field sees the key.
    fun isNewTabEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            !keyEvent.isShiftPressed &&
            !keyEvent.isAltPressed &&
            keyEvent.key.keyCode == Key.T.keyCode &&
            keyEvent.type == KeyEventType.KeyDown

    fun isCloseTabEvent(keyEvent: KeyEvent) =
        keyEvent.isCommandTrigger() &&
            !keyEvent.isShiftPressed &&
            !keyEvent.isAltPressed &&
            keyEvent.key.keyCode == Key.W.keyCode &&
            keyEvent.type == KeyEventType.KeyDown

    /** Ctrl + Tab everywhere, and Cmd/Ctrl + Shift + ] like the browsers. */
    fun isNextTabEvent(keyEvent: KeyEvent) =
        keyEvent.type == KeyEventType.KeyDown &&
            (
                (keyEvent.isCtrlPressed && !keyEvent.isShiftPressed && keyEvent.key.keyCode == Key.Tab.keyCode) ||
                    (keyEvent.isCommandTrigger() && keyEvent.isShiftPressed && keyEvent.key.keyCode == Key.RightBracket.keyCode)
            )

    /** Ctrl + Shift + Tab everywhere, and Cmd/Ctrl + Shift + [. */
    fun isPreviousTabEvent(keyEvent: KeyEvent) =
        keyEvent.type == KeyEventType.KeyDown &&
            (
                (keyEvent.isCtrlPressed && keyEvent.isShiftPressed && keyEvent.key.keyCode == Key.Tab.keyCode) ||
                    (keyEvent.isCommandTrigger() && keyEvent.isShiftPressed && keyEvent.key.keyCode == Key.LeftBracket.keyCode)
            )

    /** Cmd/Ctrl + 1...9: the index of the tab to show (from 0; 8 means the last), or null. */
    fun selectTabIndex(keyEvent: KeyEvent): Int? {
        if (!keyEvent.isCommandTrigger() || keyEvent.isShiftPressed || keyEvent.isAltPressed) return null
        if (keyEvent.type != KeyEventType.KeyDown) return null
        return tabNumberKeys.indexOf(keyEvent.key.keyCode).takeIf { it >= 0 }
    }

    private val tabNumberKeys = listOf(
        Key.One,
        Key.Two,
        Key.Three,
        Key.Four,
        Key.Five,
        Key.Six,
        Key.Seven,
        Key.Eight,
        Key.Nine
    ).map { it.keyCode }

    fun isShiftArrowUpEvent(keyEvent: KeyEvent) =
        keyEvent.isShiftPressed &&
            keyEvent.key.keyCode == Key.DirectionUp.keyCode &&
            keyEvent.type == KeyEventType.KeyDown

    fun isShiftArrowDownEvent(keyEvent: KeyEvent) =
        keyEvent.isShiftPressed &&
            keyEvent.key.keyCode == Key.DirectionDown.keyCode &&
            keyEvent.type == KeyEventType.KeyDown
}
