package io.writeopia.editor.features.summarization

import android.content.Context
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import com.google.mlkit.genai.summarization.SummarizerOptions.InputType
import com.google.mlkit.genai.summarization.SummarizerOptions.Language
import com.google.mlkit.genai.summarization.SummarizerOptions.OutputType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume

actual class SummarizationService actual constructor() {

    companion object {
        @Volatile
        private var applicationContext: Context? = null

        fun initialize(context: Context) {
            applicationContext = context.applicationContext
        }
    }

    private var summarizer: Summarizer? = null

    private fun getOrCreateSummarizer(context: Context): Summarizer {
        return summarizer ?: run {
            val options = SummarizerOptions.builder(context)
                .setInputType(InputType.ARTICLE)
                .setOutputType(OutputType.THREE_BULLETS)
                .setLanguage(Language.ENGLISH)
                .build()
            Summarization.getClient(options).also { summarizer = it }
        }
    }

    actual suspend fun summarize(text: String): Result<String> {
        val context = applicationContext
            ?: return Result.failure(
                IllegalStateException(
                    "SummarizationService not initialized. Call SummarizationService.initialize(context) first."
                )
            )

        if (text.length < 400) {
            return Result.failure(
                IllegalArgumentException("Text must be at least 400 characters for summarization")
            )
        }

        val client = getOrCreateSummarizer(context)

        return withContext(Dispatchers.IO) {
            try {
                val featureStatus = client.checkFeatureStatus().get()

                when (featureStatus) {
                    FeatureStatus.AVAILABLE -> {
                        performSummarization(client, text)
                    }
                    FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                        downloadAndSummarize(client, text)
                    }
                    else -> {
                        Result.failure(
                            UnsupportedOperationException(
                                "AI summarization is not available on this device. " +
                                    "Requires AICore service and a compatible device."
                            )
                        )
                    }
                }
            } catch (e: ExecutionException) {
                Result.failure(e.cause ?: e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun performSummarization(client: Summarizer, text: String): Result<String> {
        return try {
            val request = SummarizationRequest.builder(text).build()
            val result = client.runInference(request).get()
            Result.success(result.summary)
        } catch (e: ExecutionException) {
            Result.failure(e.cause ?: e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun downloadAndSummarize(client: Summarizer, text: String): Result<String> {
        return suspendCancellableCoroutine { continuation ->
            client.downloadFeature(object : DownloadCallback {
                override fun onDownloadStarted(bytesToDownload: Long) {}
                override fun onDownloadProgress(totalBytesDownloaded: Long) {}
                override fun onDownloadFailed(e: GenAiException) {
                    continuation.resume(
                        Result.failure(Exception("Failed to download AI model: ${e.message}"))
                    )
                }
                override fun onDownloadCompleted() {
                    val result = performSummarization(client, text)
                    continuation.resume(result)
                }
            })
        }
    }

    actual fun close() {
        summarizer?.close()
        summarizer = null
    }
}
