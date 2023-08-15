package com.github.leandroborgesferreira.storyteller.parse

import com.github.leandroborgesferreira.storyteller.preview.PreviewParser
import com.github.leandroborgesferreira.storyteller.utils.MapStoryData
import org.junit.Assert.*
import org.junit.Test

class PreviewParserTest {

    @Test
    fun `a simple document should be parsed`() {
        val input = MapStoryData.singleCheckItem().values.toList()
        val result = PreviewParser().preview(input)

        assertEquals(
            "The check item provided should be in the preview",
            input,
            result
        )
    }

    @Test
    fun `a complex document should be parsed`() {
        val maxSize = 3
        val input = MapStoryData.syncHistory().values.toList()
        val result = PreviewParser().preview(input, maxSize)

        assertEquals(
            "The check item provided should be in the preview",
            maxSize,
            result.size
        )
    }
}
