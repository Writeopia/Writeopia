package io.writeopia.common.utils.keyboard

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed

actual fun KeyEvent.isCommandTrigger(): Boolean = this.isCtrlPressed

actual fun KeyEvent.isMultiSelectionTrigger(): Boolean = this.isAltPressed && this.isMetaPressed
