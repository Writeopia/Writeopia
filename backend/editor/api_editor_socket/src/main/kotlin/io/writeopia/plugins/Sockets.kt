package io.writeopia.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import io.writeopia.socket.Connection
import java.util.Collections
import kotlin.time.Duration.Companion.seconds

fun Application.configureEditorSockets() {
    install(WebSockets) {
        pingPeriod = 10.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        val connections = Collections.synchronizedSet<Connection?>(LinkedHashSet())

        webSocket("/editor/{documentId}") {
            val documentId = call.parameters["documentId"]
            val thisConnection = Connection(this)
            connections += thisConnection

            try {
                send("You are connected to document: $documentId! There are ${connections.count()} users here.")

                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val receivedText = frame.readText()

                    println("text received: $receivedText")

                    val messageToSend = "[${thisConnection.name}]: $receivedText"
//                    connections.forEach { connection ->
//                        connection.session.send(messageToSend)
//                    }
                }
            } catch (e: Exception) {
                println(e.localizedMessage)
            } finally {
                println("Removing $thisConnection!")
                connections -= thisConnection
            }
        }
    }
}
