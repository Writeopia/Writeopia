package io.writeopia

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.writeopia.plugins.configureFirebase
import io.writeopia.plugins.configureSerialization

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module(byPassAuth: Boolean = false, forceDbInMemory: Boolean? = null) {
    val dbInMemory =
        forceDbInMemory ?: System.getenv("IN_MEMORY_DATABASE")?.let { it == "true" } ?: false

    if (!byPassAuth) {
        configureFirebase()
    }
//    configureEditorSockets()
    configureSerialization()
}
