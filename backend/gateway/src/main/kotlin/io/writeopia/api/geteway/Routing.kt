package io.writeopia.api.geteway

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.writeopia.api.core.auth.authRoute
import io.writeopia.api.documents.routing.documentsRoute
import io.writeopia.sql.WriteopiaDbBackend

fun Application.configureRouting(
    writeopiaDb: WriteopiaDbBackend,
    useAi: Boolean,
    debugMode: Boolean = false
) {
    routing {
        documentsRoute(writeopiaDb, useAi)

        get {
            call.respondText("Hi")
        }

        authRoute(writeopiaDb, debugMode)
    }
}
