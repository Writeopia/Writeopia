package io.writeopia.backend.selfhosted.config

import io.writeopia.sdk.serialization.json.writeopiaJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

object ConfigLoader {
    private const val CONFIG_FILE_NAME = "writeopia-config.json"
    private val configFile = File(System.getProperty("user.home"), ".writeopia${File.separator}$CONFIG_FILE_NAME")

    fun getConfig(): SelfHostedConfig? {
        if (!configFile.exists()) {
            createDefaultConfig()
        }

        return try {
            val content = configFile.readText()
            writeopiaJson.decodeFromString<SelfHostedConfig>(content)
        } catch (e: Exception) {
            System.err.println("Error reading config file: ${e.message}")
            null
        }
    }

    private fun createDefaultConfig() {
        try {
            configFile.parentFile.mkdirs()
            configFile.createNewFile()
            val config = SelfHostedConfig.DEFAULT
            configFile.writeText(writeopiaJson.encodeToString(config))
            println("Created default config at ${configFile.absolutePath}")
        } catch (e: Exception) {
            System.err.println("Error creating default config: ${e.message}")
        }
    }
}
