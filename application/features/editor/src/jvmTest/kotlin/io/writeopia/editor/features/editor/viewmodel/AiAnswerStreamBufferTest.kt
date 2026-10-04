package io.writeopia.editor.features.editor.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals

class AiAnswerStreamBufferTest {

    @Test
    fun plainTextWithoutHeadingsStaysInOneBlock() {
        val text = "Hello there.\nThis is a normal paragraph without any headings."
        val expected = listOf(text)

        assertEquals(expected, AiAnswerStreamBuffer.splitIntoBlocks(text))
    }

    @Test
    fun headingAtTheStartDoesNotCreateAnEmptyLeadingBlock() {
        val text = "# Title\nSome content below the title."
        val expected = listOf(text)

        assertEquals(expected, AiAnswerStreamBuffer.splitIntoBlocks(text))
    }

    @Test
    fun headingInTheMiddleStartsANewBlock() {
        val text = "Intro paragraph.\n# Section 1\nContent of section 1."
        val expected = listOf(
            "Intro paragraph.",
            "# Section 1\nContent of section 1."
        )

        assertEquals(expected, AiAnswerStreamBuffer.splitIntoBlocks(text))
    }

    @Test
    fun multipleHeadingsCreateMultipleBlocks() {
        val text = "# Title\nIntro.\n## Section 1\nBody 1.\n## Section 2\nBody 2."
        val expected = listOf(
            "# Title\nIntro.",
            "## Section 1\nBody 1.",
            "## Section 2\nBody 2."
        )

        assertEquals(expected, AiAnswerStreamBuffer.splitIntoBlocks(text))
    }

    @Test
    fun indentedHeadingsAreDetectedAfterTrimming() {
        val text = "Intro.\n   # Indented heading\nBody."
        val expected = listOf(
            "Intro.",
            "   # Indented heading\nBody."
        )

        assertEquals(expected, AiAnswerStreamBuffer.splitIntoBlocks(text))
    }

    @Test
    fun boundariesAreStableAsTextKeepsGrowing() {
        val step1 = "Intro paragraph."
        val step2 = "Intro paragraph.\n# Sect"
        val step3 = "Intro paragraph.\n# Section 1\nSome bo"
        val step4 = "Intro paragraph.\n# Section 1\nSome body text."

        assertEquals(listOf("Intro paragraph."), AiAnswerStreamBuffer.splitIntoBlocks(step1))
        assertEquals(
            listOf("Intro paragraph.", "# Sect"),
            AiAnswerStreamBuffer.splitIntoBlocks(step2)
        )
        assertEquals(
            listOf("Intro paragraph.", "# Section 1\nSome bo"),
            AiAnswerStreamBuffer.splitIntoBlocks(step3)
        )
        assertEquals(
            listOf("Intro paragraph.", "# Section 1\nSome body text."),
            AiAnswerStreamBuffer.splitIntoBlocks(step4)
        )
    }

    @Test
    fun emptyTextReturnsASingleEmptyBlock() {
        assertEquals(listOf(""), AiAnswerStreamBuffer.splitIntoBlocks(""))
    }

    @Test
    fun consecutiveHeadingsWithoutContentAreNotSplitApart() {
        val text = "# Title\n## Subtitle\nContent."
        val expected = listOf(text)

        assertEquals(expected, AiAnswerStreamBuffer.splitIntoBlocks(text))
    }

    @Test
    fun aHeadingFollowedByAnotherHeadingWithContentDoesNotLeaveALoneTitleBlock() {
        val text = "# Title\nIntro.\n## Empty\n## Another\nBody."
        val expected = listOf(
            "# Title\nIntro.",
            "## Empty\n## Another\nBody."
        )

        assertEquals(expected, AiAnswerStreamBuffer.splitIntoBlocks(text))
    }

    @Test
    fun trailingHeadingWithoutContentYetStaysAsItsOwnBlockWhileStreaming() {
        val text = "Intro.\n# Section 1\n## Subsection"
        val expected = listOf(
            "Intro.",
            "# Section 1\n## Subsection"
        )

        assertEquals(expected, AiAnswerStreamBuffer.splitIntoBlocks(text))
    }
}
