package io.writeopia.common.utils.file

expect object SaveImage {
    fun saveLocally(externalFile: String, folderPath: String): String
}
