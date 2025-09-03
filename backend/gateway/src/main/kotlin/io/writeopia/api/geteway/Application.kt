package io.writeopia.api.geteway

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.writeopia.api.core.auth.installAuth
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
    writeopiaDb: WriteopiaDbBackend? = configurePersistence(),
    useAi: Boolean = System.getenv("WRITEOPIA_USE_AI")?.toBoolean() ?: false,
    debugMode: Boolean = System.getenv("WRITEOPIA_DEBUG_MODE")?.toBoolean() ?: false,
    adminKey: String? = System.getenv("ADMIN_KEY")
) {
    println("debug: $debugMode")
    installAuth()
    configureRouting(writeopiaDb, useAi, debugMode = debugMode, adminKey = adminKey)
    configureSerialization()
    configureEditorSockets()
    configureHTTP()
}
