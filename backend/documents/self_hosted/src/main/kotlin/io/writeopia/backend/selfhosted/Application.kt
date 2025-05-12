package io.writeopia.backend.selfhosted

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import io.writeopia.api.geteway.configureRouting
import io.writeopia.backend.selfhosted.config.ConfigLoader
import io.writeopia.backend.selfhosted.config.SelfHostedConfig
import io.writeopia.backend.selfhosted.routing.selfHostedRoutes
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.sql.SqlDelightDaoBackend
import java.io.File

fun main() {
    embeddedServer(
        Netty,
        port = ConfigLoader.getConfig()?.port ?: 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(writeopiaJson)
    }

    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
    }

    val config = ConfigLoader.getConfig() ?: SelfHostedConfig.DEFAULT
    val dbFolder = File(config.databasePath).apply { mkdirs() }
    val writeopiaDb = SqlDelightDaoBackend(
        dbPath = dbFolder.absolutePath,
        appFolder = dbFolder.absolutePath,
        memoryMode = false,
        dbType = 0
    )

    configureRouting(writeopiaDb)

    routing {
        selfHostedRoutes(writeopiaDb)
    }
}
