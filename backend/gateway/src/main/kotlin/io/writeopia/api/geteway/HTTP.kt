package io.writeopia.api.geteway

import io.ktor.server.application.Application
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing

fun Application.configureHTTP() {
    routing {
        swaggerUI(path = "openapi")
    }
}
