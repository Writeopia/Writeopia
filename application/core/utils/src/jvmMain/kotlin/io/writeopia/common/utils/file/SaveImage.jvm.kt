package io.writeopia.common.utils.file

import io.writeopia.sdk.models.id.GenerateId
import java.io.File
import java.io.IOException

actual object SaveImage {
    actual fun saveLocally(externalFile: String, folderPath: String): String {
        val folder = File(folderPath)
        val external = File(externalFile)

        val hasFolder = if (!folder.exists()) {
            folder.mkdirs()
        } else {
            true
        }

        if (hasFolder && folder.isDirectory) {
            val resultImagePath = "$folderPath/${external.name}"

            val image = File(resultImagePath)
            val imageToMove = if (image.exists()) {
                File("${image.absolutePath}${GenerateId.generate()}.${image.extension}")
            } else {
                image
            }

            return try {
                external.copyTo(imageToMove, overwrite = true)
                imageToMove.absolutePath
            } catch (e: IOException) {
                println("Error: ${e.message}")
                external.absolutePath
            }
        }

        return external.absolutePath
    }
}
