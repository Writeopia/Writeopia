package io.writeopia.api.billing

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import io.writeopia.api.core.auth.utils.installAuth
import io.writeopia.databse.HikariCp
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("BillingApplication")

fun main() {
    embeddedServer(
        CIO,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

private val debugMode: Boolean
    get() = System.getenv("WRITEOPIA_DEBUG_MODE")?.toBoolean() ?: false

fun Application.module(
    writeopiaDb: WriteopiaDbBackend? = configurePersistence(),
    debugMode: Boolean = Companion.debugMode
) {
    logger.info("Billing microservice starting - debug: $debugMode")

    // Initialize Stripe
    try {
        StripeService.initialize()
    } catch (e: Exception) {
        logger.error("Failed to initialize Stripe: ${e.message}")
        if (!debugMode) {
            throw e
        }
    }

    installCORS()
    installAuth()
    configureSerialization()

    routing {
        if (writeopiaDb != null) {
            billingRoutes(writeopiaDb, debugMode)
        }
    }
}

private object Companion {
    val debugMode: Boolean
        get() = System.getenv("WRITEOPIA_DEBUG_MODE")?.toBoolean() ?: false
}

fun configurePersistence(): WriteopiaDbBackend? {
    return try {
        val driver = HikariCp.driver(debugMode)
        if (debugMode && !HikariCp.isSchemaCreated()) {
            WriteopiaDbBackend.Schema.create(driver)
            HikariCp.markSchemaCreated()
        }
        WriteopiaDbBackend(driver)
    } catch (e: Exception) {
        logger.error("Failed to configure persistence: ${e.message}")
        null
    }
}

fun Application.installCORS() {
    install(CORS) {
        allowHost("writeopia.io", schemes = listOf("https"))
        allowHost("app.writeopia.io", schemes = listOf("https"))
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}
