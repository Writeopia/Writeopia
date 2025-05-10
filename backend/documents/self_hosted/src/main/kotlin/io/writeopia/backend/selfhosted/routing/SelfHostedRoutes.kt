package io.writeopia.backend.selfhosted.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.writeopia.sql.WriteopiaDbBackend

fun Routing.selfHostedRoutes(writeopiaDb: WriteopiaDbBackend) {
    route("api/self-hosted") {
        get("/status") {
            call.respond(
                status = HttpStatusCode.OK,
                message = mapOf(
                    "status" to "running",
                    "message" to "Writeopia self-hosted backend is running"
                )
            )
        }

        get("/info") {
            call.respond(
                status = HttpStatusCode.OK,
                message = mapOf(
                    "version" to "1.0.0",
                    "name" to "Writeopia Self-Hosted Backend"
                )
            )
        }
    }
}
