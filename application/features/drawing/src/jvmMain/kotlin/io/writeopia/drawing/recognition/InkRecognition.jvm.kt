package io.writeopia.drawing.recognition

import io.writeopia.sdk.models.drawing.Stroke

actual fun createInkRecognition(): InkRecognition = NoOpInkRecognition()

/**
 * No-op implementation for JVM/Desktop.
 * Ink recognition is not available on this platform.
 */
class NoOpInkRecognition : InkRecognition {
    override suspend fun recognize(strokes: List<Stroke>): String? = null
    override fun isAvailable(): Boolean = false
}
