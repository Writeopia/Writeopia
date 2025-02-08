package io.writeopia.api.geteway

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    embeddedServer(
        Netty,
        port = 8080, // This is the port on which Ktor is listening
        host = "127.0.0.1",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    val writeopiaDb = configurePersistence()
    configureRouting(writeopiaDb)
    configureSerialization()
    configureHTTP()
}
