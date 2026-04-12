package io.writeopia.drawing.recognition

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import io.writeopia.sdk.models.drawing.Stroke
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual fun createInkRecognition(): InkRecognition = MlKitInkRecognition()

/**
 * ML Kit Digital Ink Recognition implementation for Android.
 */
class MlKitInkRecognition : InkRecognition {

    private val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
    private val model: DigitalInkRecognitionModel? = modelIdentifier?.let {
        DigitalInkRecognitionModel.builder(it).build()
    }

    private var isModelDownloaded = false

    override suspend fun recognize(strokes: List<Stroke>): String? {
        if (model == null || strokes.isEmpty()) return null

        // Ensure model is downloaded
        if (!isModelDownloaded) {
            val downloaded = downloadModel()
            if (!downloaded) return null
        }

        // Convert our strokes to ML Kit Ink format
        val inkBuilder = Ink.builder()
        strokes.forEach { stroke ->
            val strokeBuilder = Ink.Stroke.builder()
            stroke.points.forEach { point ->
                strokeBuilder.addPoint(
                    Ink.Point.create(point.x, point.y, point.timestamp)
                )
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }
        val ink = inkBuilder.build()

        // Create recognizer and recognize
        val recognizerOptions = DigitalInkRecognizerOptions.builder(model).build()
        val recognizer = DigitalInkRecognition.getClient(recognizerOptions)

        return suspendCancellableCoroutine { continuation ->
            recognizer.recognize(ink)
                .addOnSuccessListener { result ->
                    val text = result.candidates.firstOrNull()?.text
                    continuation.resume(text)
                }
                .addOnFailureListener { e ->
                    continuation.resume(null)
                }

            continuation.invokeOnCancellation {
                recognizer.close()
            }
        }
    }

    override fun isAvailable(): Boolean = model != null

    private suspend fun downloadModel(): Boolean {
        if (model == null) return false

        return suspendCancellableCoroutine { continuation ->
            val remoteModelManager = RemoteModelManager.getInstance()
            remoteModelManager.download(model, DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    isModelDownloaded = true
                    continuation.resume(true)
                }
                .addOnFailureListener {
                    continuation.resume(false)
                }
        }
    }
}
