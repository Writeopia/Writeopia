package io.writeopia.api.documents

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.writeopia.api.core.auth.utils.installAuth
import io.writeopia.api.documents.embeddings.DocumentEmbeddingService
import io.writeopia.api.embeddings.Embedder
import io.writeopia.api.embeddings.EmbeddingStore
import io.writeopia.api.embeddings.EmbeddingsSetup
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.databse.HikariCp
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend

fun main() {
    // Cloud Run injects PORT env var
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    embeddedServer(
        CIO,
        port = port,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module(
    writeopiaDb: WriteopiaDbBackend? = configurePersistence(),
    useAi: Boolean = System.getenv("WRITEOPIA_USE_AI")?.toBoolean() ?: false,
    debugMode: Boolean = System.getenv("WRITEOPIA_DEBUG_MODE")?.toBoolean() ?: false,
    stagingMode: Boolean = System.getenv("WRITEOPIA_STAGING_MODE")?.toBoolean() ?: false,
    useCloudAi: Boolean = System.getenv("WRITEOPIA_USE_CLOUD_AI")?.toBoolean() ?: false,
    /** The embeddings to use instead of Gemini + pgvector from the environment; tests pass fakes. */
    embedder: Embedder? = null,
    embeddingStore: EmbeddingStore? = null
) {
    val genAiService = if (useCloudAi) GenAiService() else null
    val embeddingService = embeddingService(genAiService, writeopiaDb != null, embedder, embeddingStore)

    logger.info("Documents microservice starting - debug: $debugMode, staging: $stagingMode, useAi: $useAi, useCloudAi: $useCloudAi")
    installCORS(stagingMode)
    installAuth()
    configureRouting(
        writeopiaDb,
        useAi,
        debugMode = debugMode,
        genAiService = genAiService,
        embeddingService = embeddingService
    )
    configureSerialization()
}

/**
 * The embeddings of documents and folders: the given fakes, else Gemini and pgvector when the
 * cloud AI and the database are configured, else none.
 */
private fun embeddingService(
    genAiService: GenAiService?,
    hasDatabase: Boolean,
    embedder: Embedder?,
    embeddingStore: EmbeddingStore?
): DocumentEmbeddingService? {
    if (embedder != null && embeddingStore != null) return DocumentEmbeddingService(embedder, embeddingStore)
    if (!hasDatabase) return null

    return EmbeddingsSetup.create(genAiService, HikariCp.driver())
        ?.let { embeddings -> DocumentEmbeddingService(embeddings.embedder, embeddings.store) }
}

fun Application.installCORS(stagingMode: Boolean = false) {
    install(CORS) {
        allowHost("writeopia.io", schemes = listOf("https"))
        allowHost("app.writeopia.io", schemes = listOf("https"))

        // Allow any origin in staging mode for local development
        if (stagingMode) {
            anyHost()
        }

        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }
}
