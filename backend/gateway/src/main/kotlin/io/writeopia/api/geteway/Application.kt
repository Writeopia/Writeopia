package io.writeopia.api.geteway

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.writeopia.api.core.auth.utils.installAuth
import io.writeopia.connection.logger
import io.writeopia.plugins.configureEditorSockets
import io.writeopia.sql.WriteopiaDbBackend

fun main() {
    embeddedServer(
        CIO,
        port = 8080,
//        host = "127.0.0.1",
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module(
    writeopiaDb: WriteopiaDbBackend? = null,
    useAi: Boolean = System.getenv("WRITEOPIA_USE_AI")?.toBoolean() ?: false,
    debugMode: Boolean = System.getenv("WRITEOPIA_DEBUG_MODE")?.toBoolean() ?: false,
    stagingMode: Boolean = System.getenv("WRITEOPIA_STAGING_MODE")?.toBoolean() ?: false,
    adminKey: String? = System.getenv("ADMIN_KEY")
) {
    val db = writeopiaDb ?: configurePersistence()
    logger.info("debug: $debugMode, staging: $stagingMode")
    installCORS(stagingMode)
    installAuth()
    configureRouting(db, useAi, debugMode = debugMode, adminKey = adminKey)
    configureSerialization()
    configureEditorSockets()
    configureHTTP()
}

fun Application.installCORS(stagingMode: Boolean = false) {
    install(CORS) {
        allowHost("writeopia.io", schemes = listOf("https"))
        allowHost("app.writeopia.io", schemes = listOf("https"))

        // Allow any origin in staging mode for local development
        if (stagingMode) {
            anyHost()
        }

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
