package io.writeopia.ai.task

import io.writeopia.LocalAiRepository
import io.writeopia.responses.DownloadModelResponse
import io.writeopia.sdk.models.utils.ResultData
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Downloads [modelName] from [providerUrl] as a [AiTaskType.MODEL_DOWNLOAD] task, so its progress
 * shows with the other AI tasks. The download runs in the scope of the task manager, so it keeps
 * going after the screen that started it is gone.
 *
 * [onResult] receives every update of the download, for screens that show it themselves.
 */
@OptIn(ExperimentalTime::class)
fun AiTaskManager.enqueueModelDownload(
    localAiRepository: LocalAiRepository,
    modelName: String,
    providerUrl: String,
    onResult: (ResultData<DownloadModelResponse>) -> Unit = {},
) {
    val taskId = "download-model-$modelName-${Clock.System.now()}"

    enqueueTask(
        id = taskId,
        type = AiTaskType.MODEL_DOWNLOAD,
        description = "Downloading $modelName"
    ) {
        var lastResult: ResultData<DownloadModelResponse>? = null

        localAiRepository.downloadModel(modelName, providerUrl)
            .collect { result ->
                lastResult = result

                when (result) {
                    is ResultData.Complete -> updateTaskProgress(taskId, 1.0f)

                    is ResultData.InProgress -> {
                        val total = result.data.total
                        val completed = result.data.completed
                        if (total != null && completed != null && total > 0) {
                            updateTaskProgress(taskId, completed.toFloat() / total.toFloat())
                        }
                    }

                    else -> {}
                }

                onResult(result)
            }

        when (val finalResult = lastResult) {
            is ResultData.Complete -> Result.success(Unit)
            is ResultData.Error -> Result.failure(
                finalResult.exception ?: Exception("Download failed")
            )
            else -> Result.failure(Exception("Download did not complete"))
        }
    }
}
