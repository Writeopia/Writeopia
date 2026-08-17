package io.writeopia.api.export.service

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipService {
    private val outputStream = ByteArrayOutputStream()
    private val zipOutputStream = ZipOutputStream(outputStream)

    fun addEntry(path: String, content: ByteArray) {
        val entry = ZipEntry(path)
        zipOutputStream.putNextEntry(entry)
        zipOutputStream.write(content)
        zipOutputStream.closeEntry()
    }

    fun addEntry(path: String, content: String) {
        addEntry(path, content.toByteArray(Charsets.UTF_8))
    }

    fun finish(): ByteArray {
        zipOutputStream.close()
        return outputStream.toByteArray()
    }
}
