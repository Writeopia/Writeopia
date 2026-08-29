package io.writeopia.api.geteway

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.writeopia.api.ai.routing.aiRoute
import io.writeopia.api.core.auth.routing.adminProtectedRoute
import io.writeopia.api.core.auth.routing.authRoute
import io.writeopia.api.core.auth.routing.cookieAuthRoute
import io.writeopia.api.core.auth.routing.passwordResetRoute
import io.writeopia.api.core.auth.routing.workspaceRoute
import io.writeopia.api.documents.routing.documentsRoute
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend

fun Application.configureRouting(
    writeopiaDb: WriteopiaDbBackend?,
    useAi: Boolean,
    debugMode: Boolean = false,
    adminKey: String?
) {
    val useCloudAi = System.getenv("WRITEOPIA_USE_CLOUD_AI")?.toBoolean() == true
    val genAiService = if (useCloudAi) GenAiService() else null

    routing {
        if (writeopiaDb != null) {
            documentsRoute(writeopiaDb, useAi, debugMode, genAiService = genAiService)

            authRoute(writeopiaDb, debugMode)

            // Web-specific auth routes using HttpOnly cookies
            cookieAuthRoute(writeopiaDb, debugMode)

            workspaceRoute(adminKey, writeopiaDb, debugMode)

            passwordResetRoute(writeopiaDb)

            if (adminKey != null || debugMode) {
                logger.info("Admin routes are enabled.")
                adminProtectedRoute(adminKey, writeopiaDb, debugMode)
            } else {
                logger.info("Admin key is null. Admin routes are disabled.")
            }
        }

        if (useCloudAi) {
            logger.info("Cloud AI routes are enabled.")
            aiRoute(debugMode)
        } else {
            logger.info("Cloud AI routes are disabled. Set WRITEOPIA_USE_CLOUD_AI=true to enable.")
        }

        get {
            call.respondText("Hi")
        }
    }
}
