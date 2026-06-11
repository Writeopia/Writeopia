package io.writeopia.api.auth

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
    embeddedServer(
        CIO,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module(
    writeopiaDb: WriteopiaDbBackend? = configurePersistence(),
    debugMode: Boolean = System.getenv("WRITEOPIA_DEBUG_MODE")?.toBoolean() ?: false,
    adminKey: String? = System.getenv("ADMIN_KEY")
) {
    logger.info("Auth microservice starting - debug: $debugMode")
    installCORS()
    installAuth()
    configureRouting(writeopiaDb, debugMode = debugMode, adminKey = adminKey)
    configureSerialization()
}

fun Application.installCORS() {
    install(CORS) {
        allowHost("writeopia.io", schemes = listOf("https"))
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Admin-KEY")
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }
}
