package io.writeopia.backend.selfhosted.config

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class SelfHostedConfig(
    val port: Int,
    val databasePath: String,
    val debug: Boolean = false
) {
    companion object {
        val DEFAULT = SelfHostedConfig(
            port = 8080,
            databasePath = "${System.getProperty("user.home")}${File.separator}.writeopia${File.separator}db",
            debug = false
        )
    }
}
