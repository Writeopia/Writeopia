package io.writeopia.ui.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkDetectorTest {

    @Test
    fun recognizesWebAddresses() {
        assertTrue(LinkDetector.isLink("https://writeopia.io"))
        assertTrue(LinkDetector.isLink("http://example.com/path?query=1#frag"))
        assertTrue(LinkDetector.isLink("  https://writeopia.io/docs  "))
        assertTrue(LinkDetector.isLink("HTTPS://WRITEOPIA.IO"))
    }

    @Test
    fun rejectsTextThatIsNotOnlyAnAddress() {
        assertFalse(LinkDetector.isLink(""))
        assertFalse(LinkDetector.isLink("writeopia.io"))
        assertFalse(LinkDetector.isLink("https://"))
        assertFalse(LinkDetector.isLink("see https://writeopia.io"))
        assertFalse(LinkDetector.isLink("https://writeopia.io and more"))
        assertFalse(LinkDetector.isLink("ftp://files.example.com"))
    }

    @Test
    fun aPasteIsAWholeLinkArrivingInAnEmptyLine() {
        assertTrue(LinkDetector.isPastedLink(previousText = null, newText = "https://writeopia.io"))
        assertTrue(LinkDetector.isPastedLink(previousText = "", newText = "https://writeopia.io"))
        assertFalse(LinkDetector.isPastedLink(previousText = "h", newText = "ht"))
        assertFalse(LinkDetector.isPastedLink(previousText = "see ", newText = "see https://writeopia.io"))
    }
}
