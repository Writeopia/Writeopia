package io.writeopia.ui.spellcheck.macos

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Memory

/**
 * Bridge to macOS NSSpellChecker via JNA and Objective-C runtime.
 * Provides spell checking functionality using the native macOS spell checker.
 *
 * Note: This implementation uses a word-by-word approach to avoid complex
 * struct return handling issues with JNA on ARM64.
 */
class NSSpellCheckerBridge private constructor() {

    private val runtime: ObjCRuntime = ObjCRuntime.INSTANCE
        ?: throw IllegalStateException("Failed to load Objective-C runtime")

    private val spellChecker: Pointer

    init {
        // Get NSSpellChecker class
        val nsSpellCheckerClass = runtime.objc_getClass("NSSpellChecker")
            ?: throw IllegalStateException("Failed to get NSSpellChecker class")

        // Get sharedSpellChecker selector
        val sharedSpellCheckerSel = runtime.sel_registerName("sharedSpellChecker")
            ?: throw IllegalStateException("Failed to register sharedSpellChecker selector")

        // Get the shared instance
        spellChecker = runtime.objc_msgSend(nsSpellCheckerClass, sharedSpellCheckerSel)
            ?: throw IllegalStateException("Failed to get sharedSpellChecker instance")
    }

    /**
     * Checks spelling of a string and returns ranges of misspelled words.
     * Uses word-by-word checking to avoid complex NSRange return handling.
     *
     * @param text The text to check for spelling errors
     * @return List of IntRange representing the positions of misspelled words
     */
    fun checkSpelling(text: String): List<IntRange> {
        if (text.isEmpty()) return emptyList()

        val misspelledRanges = mutableListOf<IntRange>()

        // Use word-by-word approach with countWordsInString to verify spelling
        val words = extractWords(text)

        for ((word, startIndex) in words) {
            if (word.length > 1 && !isWordCorrect(word)) {
                misspelledRanges.add(startIndex until (startIndex + word.length))
            }
        }

        return misspelledRanges
    }

    private fun extractWords(text: String): List<Pair<String, Int>> {
        val words = mutableListOf<Pair<String, Int>>()
        val wordRegex = Regex("[a-zA-Z]+")

        wordRegex.findAll(text).forEach { match ->
            words.add(match.value to match.range.first)
        }

        return words
    }

    /**
     * Check if a single word is spelled correctly using hasLearnedWord or spell check.
     * We use a technique that returns a simple boolean rather than NSRange.
     */
    private fun isWordCorrect(word: String): Boolean {
        // Use checkSpellingOfString:startingAt: and check if it returns NSNotFound
        // NSNotFound location means no misspelling found

        val nsString = createNSString(word) ?: return true

        val checkSel = runtime.sel_registerName("checkSpellingOfString:startingAt:")
            ?: return true

        // On ARM64, objc_msgSend for NSRange returns location in x0
        // If location == NSNotFound (very large number), word is correct
        val result = CheckSpellingSimple.INSTANCE?.objc_msgSend_check(
            spellChecker,
            checkSel,
            nsString,
            NativeLong(0)
        ) ?: return true

        // NSNotFound is typically -1 cast to unsigned, which is Long.MAX_VALUE or similar
        // If the returned location is 0 and the word has content, it's misspelled
        // If returned location is >= word length or negative-ish, no misspelling found
        val location = result.toLong()

        // A location of 0 with our single-word string means the word is misspelled
        // Any other value (especially NSNotFound which is very large) means it's correct
        return location != 0L
    }

    private fun createNSString(text: String): Pointer? {
        val nsStringClass = runtime.objc_getClass("NSString") ?: return null
        val sel = runtime.sel_registerName("stringWithUTF8String:") ?: return null

        // Create a null-terminated UTF-8 byte array
        val utf8Bytes = text.toByteArray(Charsets.UTF_8)
        val memory = Memory((utf8Bytes.size + 1).toLong())
        memory.write(0, utf8Bytes, 0, utf8Bytes.size)
        memory.setByte(utf8Bytes.size.toLong(), 0)

        return runtime.objc_msgSend(nsStringClass, sel, memory)
    }

    companion object {
        @Volatile
        private var instance: NSSpellCheckerBridge? = null

        /**
         * Gets the singleton instance of NSSpellCheckerBridge.
         * Returns null if not running on macOS or if initialization fails.
         */
        fun getInstance(): NSSpellCheckerBridge? {
            if (!isMacOS()) return null

            return instance ?: synchronized(this) {
                instance ?: try {
                    NSSpellCheckerBridge().also { instance = it }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }

        /**
         * Checks if the current platform is macOS.
         */
        fun isMacOS(): Boolean {
            val osName = System.getProperty("os.name")?.lowercase() ?: return false
            return osName.contains("mac") || osName.contains("darwin")
        }
    }
}

/**
 * JNA interface that captures just the first register (location) from NSRange return.
 * On ARM64, NSRange.location is returned in x0 register.
 */
interface CheckSpellingSimple : Library {
    companion object {
        val INSTANCE: CheckSpellingSimple? by lazy {
            try {
                val options = mapOf(
                    Library.OPTION_FUNCTION_MAPPER to
                        com.sun.jna.FunctionMapper { _, method ->
                            if (method.name.startsWith("objc_msgSend")) "objc_msgSend" else method.name
                        }
                )
                Native.load("objc", CheckSpellingSimple::class.java, options)
            } catch (e: UnsatisfiedLinkError) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Calls checkSpellingOfString:startingAt: and returns just the location part.
     * On ARM64, this captures x0 register which contains NSRange.location.
     */
    fun objc_msgSend_check(
        receiver: Pointer?,
        selector: Pointer?,
        string: Pointer?,
        startingAt: NativeLong
    ): NativeLong
}
