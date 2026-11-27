@file:OptIn(InternalComposeUiApi::class)

package io.writeopia.common.utils.keyboard

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabKeyboardCommandsTest {

    private val isMac = hostOs == OS.MacOS

    /** Cmd on the Mac, Ctrl elsewhere: what isCommandTrigger reads. */
    private fun command(
        key: Key,
        type: KeyEventType = KeyEventType.KeyDown,
        shift: Boolean = false,
        alt: Boolean = false,
    ) = KeyEvent(
        key = key,
        type = type,
        isMetaPressed = isMac,
        isCtrlPressed = !isMac,
        isShiftPressed = shift,
        isAltPressed = alt,
    )

    private fun ctrl(key: Key, type: KeyEventType = KeyEventType.KeyDown, shift: Boolean = false) =
        KeyEvent(key = key, type = type, isCtrlPressed = true, isShiftPressed = shift)

    @Test
    fun newAndCloseTab() {
        assertTrue(KeyboardCommands.isNewTabEvent(command(Key.T)))
        assertTrue(KeyboardCommands.isCloseTabEvent(command(Key.W)))

        assertFalse(KeyboardCommands.isNewTabEvent(command(Key.T, type = KeyEventType.KeyUp)))
        assertFalse(KeyboardCommands.isNewTabEvent(command(Key.T, shift = true)))
        assertFalse(KeyboardCommands.isCloseTabEvent(command(Key.W, alt = true)))
        assertFalse(KeyboardCommands.isNewTabEvent(KeyEvent(key = Key.T, type = KeyEventType.KeyDown)))
    }

    @Test
    fun ctrlTabMovesBetweenTabs() {
        assertTrue(KeyboardCommands.isNextTabEvent(ctrl(Key.Tab)))
        assertFalse(KeyboardCommands.isPreviousTabEvent(ctrl(Key.Tab)))

        assertTrue(KeyboardCommands.isPreviousTabEvent(ctrl(Key.Tab, shift = true)))
        assertFalse(KeyboardCommands.isNextTabEvent(ctrl(Key.Tab, shift = true)))
    }

    @Test
    fun commandShiftBracketsMoveBetweenTabs() {
        assertTrue(KeyboardCommands.isNextTabEvent(command(Key.RightBracket, shift = true)))
        assertTrue(KeyboardCommands.isPreviousTabEvent(command(Key.LeftBracket, shift = true)))
        assertFalse(KeyboardCommands.isNextTabEvent(command(Key.RightBracket)))
    }

    @Test
    fun ctrlTabIsNotAcceptingTheAiSuggestion() {
        assertFalse(KeyboardCommands.isAcceptAiEvent(ctrl(Key.Tab, type = KeyEventType.KeyUp)))
        assertTrue(
            KeyboardCommands.isAcceptAiEvent(KeyEvent(key = Key.Tab, type = KeyEventType.KeyUp))
        )
    }

    @Test
    fun commandNumbersSelectTabs() {
        assertEquals(0, KeyboardCommands.selectTabIndex(command(Key.One)))
        assertEquals(8, KeyboardCommands.selectTabIndex(command(Key.Nine)))
        assertNull(KeyboardCommands.selectTabIndex(command(Key.Zero)))
        assertNull(KeyboardCommands.selectTabIndex(command(Key.One, shift = true)))
        assertNull(KeyboardCommands.selectTabIndex(command(Key.One, type = KeyEventType.KeyUp)))
        assertNull(KeyboardCommands.selectTabIndex(KeyEvent(key = Key.One, type = KeyEventType.KeyDown)))
    }
}
