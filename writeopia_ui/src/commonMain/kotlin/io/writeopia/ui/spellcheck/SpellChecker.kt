package io.writeopia.ui.spellcheck

expect class SpellChecker() {
    suspend fun checkSpelling(text: String): List<IntRange>
    fun isAvailable(): Boolean
}
