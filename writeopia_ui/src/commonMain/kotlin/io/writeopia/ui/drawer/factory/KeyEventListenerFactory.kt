package io.writeopia.ui.drawer.factory

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.TextFieldValue
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.command.TypeInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.EmptyErase

object KeyEventListenerFactory {

    fun desktop(
        manager: WriteopiaStateManager,
    ): (KeyEvent, TextFieldValue, StoryStep, Int, EmptyErase, Int, EndOfText) -> Boolean {
        return { keyEvent, inputText, step, position, onEmptyErase, cursorPosition, endOfText ->
            when {
                isEmptyErase(keyEvent, inputText) -> {
                    when (onEmptyErase) {
                        EmptyErase.DELETE -> {
                            if (step.tags.any { it.tag.isErasable() }) {
                                manager.removeTags(position)
                            } else {
                                manager.onErase(Action.EraseStory(step, position))
                            }
                        }

                        EmptyErase.CHANGE_TYPE -> {
                            manager.changeStoryType(position, TypeInfo(StoryTypes.TEXT.type), null)
                        }

                        EmptyErase.DISABLED -> {}
                    }

                    true
                }

                isMoveUpEventEnd(keyEvent) && endOfText.shouldMoveUp() -> {
                    manager.moveToPrevious(cursor = cursorPosition)
                    true
                }

                isMoveStrongUpEvent(keyEvent) -> {
                    manager.moveToPrevious(cursor = cursorPosition, 10)
                    true
                }

                isMoveDownEventEnd(keyEvent) && endOfText.shouldMoveDown() -> {
                    manager.moveToNext(cursor = cursorPosition)
                    true
                }

                isMoveStrongDownEvent(keyEvent) -> {
                    manager.moveToNext(cursor = cursorPosition, 10)
                    true
                }

                else -> false
            }
        }
    }

    fun android(
        manager: WriteopiaStateManager,
        isEmptyErase: (KeyEvent, TextFieldValue) -> Boolean = { _, _ -> false },
    ): (KeyEvent, TextFieldValue, StoryStep, Int, EmptyErase, Int, EndOfText) -> Boolean {
        return { keyEvent, inputText, step, position, onEmptyErase, cursorPosition, endOfText ->
            when {
                isEmptyErase(keyEvent, inputText) -> {
                    when (onEmptyErase) {
                        EmptyErase.DELETE -> {
                            if (step.tags.any { it.tag.isErasable() }) {
                                manager.removeTags(position)
                            } else {
                                manager.onErase(Action.EraseStory(step, position))
                            }
                        }

                        EmptyErase.CHANGE_TYPE -> {
                            manager.changeStoryType(position, TypeInfo(StoryTypes.TEXT.type), null)
                        }

                        EmptyErase.DISABLED -> {}
                    }

                    true
                }

                isMoveUpEventEnd(keyEvent) && endOfText.shouldMoveUp() -> {
                    manager.moveToPrevious(cursor = cursorPosition)
                    true
                }

                isMoveStrongUpEvent(keyEvent) -> {
                    manager.moveToPrevious(cursor = cursorPosition, 10)
                    true
                }

                isMoveDownEventEnd(keyEvent) && endOfText.shouldMoveDown() -> {
                    manager.moveToNext(cursor = cursorPosition)
                    true
                }

                isMoveStrongDownEvent(keyEvent) -> {
                    manager.moveToNext(cursor = cursorPosition, 10)
                    true
                }

                else -> false
            }
        }
    }
}

enum class EndOfText {
    SINGLE_LINE,
    FIRST_LINE,
    LAST_LINE,
    UNKNOWN;

    fun shouldMoveDown() = this == SINGLE_LINE || this == LAST_LINE

    fun shouldMoveUp() = this == SINGLE_LINE || this == FIRST_LINE
}

private fun isEmptyErase(
    keyEvent: KeyEvent,
    input: TextFieldValue
): Boolean = keyEvent.key == Key.Backspace
    && keyEvent.type == KeyEventType.KeyUp
    && input.selection.start == 0
    && input.selection.end == 0

private fun isMoveUpEventEnd(keyEvent: KeyEvent) =
    keyEvent.key == Key.DirectionUp
        && keyEvent.type == KeyEventType.KeyDown
        && !keyEvent.isMetaPressed

private fun isMoveDownEventEnd(keyEvent: KeyEvent) =
    keyEvent.key == Key.DirectionDown
        && keyEvent.type == KeyEventType.KeyDown
        && !keyEvent.isMetaPressed

private fun isMoveStrongUpEvent(keyEvent: KeyEvent) =
    keyEvent.key == Key.DirectionUp
        && keyEvent.type == KeyEventType.KeyDown
        && keyEvent.isMetaPressed

private fun isMoveStrongDownEvent(keyEvent: KeyEvent) =
    keyEvent.key == Key.DirectionDown
        && keyEvent.type == KeyEventType.KeyDown
        && keyEvent.isMetaPressed

private fun isLineBreak(keyEvent: KeyEvent): Boolean = keyEvent.key == Key.Enter

fun Key.isEnterKey(): Boolean =
    this.keyCode == Key.Enter.keyCode || this.keyCode == Key.NumPadEnter.keyCode
