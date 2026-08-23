package io.writeopia.api.ai.routing

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.writeopia.api.ai.model.AiGenerateRequest
import io.writeopia.api.ai.model.AiGenerateResponse
import io.writeopia.api.ai.service.GenAiService
import io.writeopia.connection.logger
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Routing.aiRoute(debugMode: Boolean = false) {
    val genAiService = GenAiService()
    val json = Json { encodeDefaults = true }

    authenticate("auth-jwt", optional = debugMode) {
        get("/api/ai/status") {
            val available = genAiService.isAvailable()
            call.respond(
                HttpStatusCode.OK,
                mapOf("available" to available)
            )
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/generate") {
            try {
                val request = call.receive<AiGenerateRequest>()

                if (request.stream) {
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        genAiService.streamGenerate(request.prompt, request.model)
                            .onEach { response ->
                                write("data: ${json.encodeToString(response)}\n\n")
                                flush()
                            }
                            .collect()
                    }
                } else {
                    val response = genAiService.generate(request.prompt, request.model)
                    if (response.error != null) {
                        call.respond(HttpStatusCode.InternalServerError, response)
                    } else {
                        call.respond(HttpStatusCode.OK, response)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error in AI generate endpoint", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AiGenerateResponse(error = e.message ?: "Unknown error")
                )
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/summary") {
            try {
                val request = call.receive<AiGenerateRequest>()

                if (request.stream) {
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        genAiService.streamSummary(request.prompt, request.model)
                            .onEach { response ->
                                write("data: ${json.encodeToString(response)}\n\n")
                                flush()
                            }
                            .collect()
                    }
                } else {
                    val response = genAiService.generateSummary(request.prompt, request.model)
                    if (response.error != null) {
                        call.respond(HttpStatusCode.InternalServerError, response)
                    } else {
                        call.respond(HttpStatusCode.OK, response)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error in AI summary endpoint", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AiGenerateResponse(error = e.message ?: "Unknown error")
                )
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/action-points") {
            try {
                val request = call.receive<AiGenerateRequest>()

                if (request.stream) {
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        genAiService.streamActionPoints(request.prompt, request.model)
                            .onEach { response ->
                                write("data: ${json.encodeToString(response)}\n\n")
                                flush()
                            }
                            .collect()
                    }
                } else {
                    val response = genAiService.generateActionPoints(request.prompt, request.model)
                    if (response.error != null) {
                        call.respond(HttpStatusCode.InternalServerError, response)
                    } else {
                        call.respond(HttpStatusCode.OK, response)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error in AI action-points endpoint", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AiGenerateResponse(error = e.message ?: "Unknown error")
                )
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/faq") {
            try {
                val request = call.receive<AiGenerateRequest>()

                if (request.stream) {
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        genAiService.streamFaq(request.prompt, request.model)
                            .onEach { response ->
                                write("data: ${json.encodeToString(response)}\n\n")
                                flush()
                            }
                            .collect()
                    }
                } else {
                    val response = genAiService.generateFaq(request.prompt, request.model)
                    if (response.error != null) {
                        call.respond(HttpStatusCode.InternalServerError, response)
                    } else {
                        call.respond(HttpStatusCode.OK, response)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error in AI faq endpoint", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AiGenerateResponse(error = e.message ?: "Unknown error")
                )
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/tags") {
            try {
                val request = call.receive<AiGenerateRequest>()

                if (request.stream) {
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        genAiService.streamTags(request.prompt, request.model)
                            .onEach { response ->
                                write("data: ${json.encodeToString(response)}\n\n")
                                flush()
                            }
                            .collect()
                    }
                } else {
                    val response = genAiService.generateTags(request.prompt, request.model)
                    if (response.error != null) {
                        call.respond(HttpStatusCode.InternalServerError, response)
                    } else {
                        call.respond(HttpStatusCode.OK, response)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error in AI tags endpoint", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AiGenerateResponse(error = e.message ?: "Unknown error")
                )
            }
        }
    }
}
