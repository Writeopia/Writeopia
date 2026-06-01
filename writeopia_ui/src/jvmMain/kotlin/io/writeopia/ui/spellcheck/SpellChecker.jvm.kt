package io.writeopia.ui.spellcheck

import io.writeopia.ui.spellcheck.macos.NSSpellCheckerBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class SpellChecker actual constructor() {

    private val bridge: NSSpellCheckerBridge? by lazy {
        NSSpellCheckerBridge.getInstance()
    }

    actual suspend fun checkSpelling(text: String): List<IntRange> {
        val spellChecker = bridge ?: return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                spellChecker.checkSpelling(text)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    actual fun isAvailable(): Boolean = bridge != null
}
