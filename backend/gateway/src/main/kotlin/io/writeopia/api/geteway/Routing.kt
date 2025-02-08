package io.writeopia.api.geteway

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.writeopia.api.documents.routing.documentsRoute
import io.writeopia.sdk.sql.WriteopiaDb

fun Application.configureRouting(writeopiaDb: WriteopiaDb) {
    routing {
        documentsRoute(writeopiaDb)

        get("/") {
            call.respondText("Hi")
        }
    }
}
