package io.writeopia.ui.spellcheck

actual class SpellChecker actual constructor() {
    actual suspend fun checkSpelling(text: String): List<IntRange> = emptyList()
    actual fun isAvailable(): Boolean = false
}
