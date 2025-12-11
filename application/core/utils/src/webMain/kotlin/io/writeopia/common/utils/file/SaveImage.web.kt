package io.writeopia.common.utils.file

actual object SaveImage {
    actual fun saveLocally(externalFile: String, folderPath: String): String = externalFile
}
