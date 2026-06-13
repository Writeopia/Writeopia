package io.writeopia.api.documents

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.writeopia.api.core.auth.utils.installAuth
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend

fun main() {
    // Cloud Run injects PORT env var
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    embeddedServer(
        CIO,
        port = port,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module(
    writeopiaDb: WriteopiaDbBackend? = configurePersistence(),
    useAi: Boolean = System.getenv("WRITEOPIA_USE_AI")?.toBoolean() ?: false,
    debugMode: Boolean = System.getenv("WRITEOPIA_DEBUG_MODE")?.toBoolean() ?: false
) {
    logger.info("Documents microservice starting - debug: $debugMode, useAi: $useAi")
    installCORS()
    installAuth()
    configureRouting(writeopiaDb, useAi, debugMode = debugMode)
    configureSerialization()
}

fun Application.installCORS() {
    install(CORS) {
        allowHost("writeopia.io", schemes = listOf("https"))
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }
}
