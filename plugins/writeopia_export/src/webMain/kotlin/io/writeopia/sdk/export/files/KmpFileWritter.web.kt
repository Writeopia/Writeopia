package io.writeopia.sdk.export.files

actual class KmpFileWriter actual constructor(fileName: String) :
    io.writeopia.sdk.utils.files.KmpClosable {
    actual override fun start() {
    }

    actual fun writeLine(line: String?) {
    }

    actual override fun close() {
    }

    actual inline fun <reified T> writeObject(data: T, json: kotlinx.serialization.json.Json) {
    }
}
