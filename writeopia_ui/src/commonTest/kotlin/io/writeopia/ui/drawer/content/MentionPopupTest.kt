package io.writeopia.ui.drawer.content

import io.writeopia.ui.drawer.content.MentionDetectionResult
import io.writeopia.ui.drawer.content.detectMentionState
import kotlin.test.Test
import kotlin.test.assertEquals

class MentionPopupTest {
    @Test
    fun `detectMentionState activates when at symbol typed at start of line`() {
        val result = detectMentionState(
            text = "@",
            cursorPos = 1,
            sizeDifference = 1,
            isPopupCurrentlyShown = false,
            currentStartPosition = -1,
        )

        assertEquals(MentionDetectionResult.Active(startPosition = 0, filter = ""), result)
    }

    @Test
    fun `detectMentionState activates when at symbol typed after space`() {
        val result = detectMentionState(
            text = "Hello @",
            cursorPos = 7,
            sizeDifference = 1,
            isPopupCurrentlyShown = false,
            currentStartPosition = -1,
        )

        assertEquals(MentionDetectionResult.Active(startPosition = 6, filter = ""), result)
    }

    @Test
    fun `detectMentionState ignores at symbol inside an email`() {
        val result = detectMentionState(
            text = "user@",
            cursorPos = 5,
            sizeDifference = 1,
            isPopupCurrentlyShown = false,
            currentStartPosition = -1,
        )

        assertEquals(MentionDetectionResult.Unchanged, result)
    }

    @Test
    fun `detectMentionState updates filter while typing after at symbol`() {
        val result = detectMentionState(
            text = "Hello @al",
            cursorPos = 9,
            sizeDifference = 1,
            isPopupCurrentlyShown = true,
            currentStartPosition = 6,
        )

        assertEquals(MentionDetectionResult.Active(startPosition = 6, filter = "al"), result)
    }

    @Test
    fun `detectMentionState dismisses when space typed after at symbol`() {
        val result = detectMentionState(
            text = "Hello @al ",
            cursorPos = 10,
            sizeDifference = 1,
            isPopupCurrentlyShown = true,
            currentStartPosition = 6,
        )

        assertEquals(MentionDetectionResult.Dismissed, result)
    }

    @Test
    fun `detectMentionState dismisses when at symbol is deleted`() {
        val result = detectMentionState(
            text = "Hello ",
            cursorPos = 6,
            sizeDifference = -1,
            isPopupCurrentlyShown = true,
            currentStartPosition = 6,
        )

        assertEquals(MentionDetectionResult.Dismissed, result)
    }
}
