package io.writeopia.api.ai

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.writeopia.connection.logger

fun main() {
    embeddedServer(
        CIO,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module(
    debugMode: Boolean = System.getenv("WRITEOPIA_DEBUG_MODE")?.toBoolean() ?: false
) {
    logger.info("AI microservice starting - debug: $debugMode")
    installCORS()
    installAuth()
    configureRouting(debugMode = debugMode)
    configureSerialization()
}

fun Application.installCORS() {
    install(CORS) {
        allowHost("writeopia.io", schemes = listOf("https"))
        allowHost("app.writeopia.io", schemes = listOf("https"))
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
    }
}
