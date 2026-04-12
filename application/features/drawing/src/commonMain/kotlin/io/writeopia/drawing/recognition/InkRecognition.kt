package io.writeopia.drawing.recognition

import io.writeopia.sdk.models.drawing.Stroke

/**
 * Interface for handwriting/ink recognition.
 */
interface InkRecognition {
    /**
     * Recognize text from a list of strokes.
     * @return The recognized text, or null if recognition fails or is unavailable.
     */
    suspend fun recognize(strokes: List<Stroke>): String?

    /**
     * Check if ink recognition is available on this platform.
     */
    fun isAvailable(): Boolean
}

/**
 * Factory function to create a platform-specific InkRecognition implementation.
 */
expect fun createInkRecognition(): InkRecognition
