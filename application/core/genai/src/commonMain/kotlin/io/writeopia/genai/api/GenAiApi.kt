package io.writeopia.genai.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.genai.model.GenAiRequest
import io.writeopia.genai.model.GenAiResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class GenAiApi(
    private val client: HttpClient,
    private val json: Json,
    private val baseUrl: String,
    private val getAuthToken: suspend () -> String?
) {
    private val generateMutex = Mutex()

    suspend fun checkStatus(): ResultData<Boolean> = try {
        val response = client.get("$baseUrl/${EndPoints.aiStatus()}") {
            contentType(ContentType.Application.Json)
            getAuthToken()?.let { token ->
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
        val body = response.body<Map<String, Boolean>>()
        ResultData.Complete(body["available"] == true)
    } catch (e: Exception) {
        ResultData.Error(e)
    }

    suspend fun generate(prompt: String, model: String? = null): ResultData<GenAiResponse> =
        generateMutex.withLock {
            try {
                val response = client.post("$baseUrl/${EndPoints.aiGenerate()}") {
                    contentType(ContentType.Application.Json)
                    getAuthToken()?.let { token ->
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(GenAiRequest(prompt, model, stream = false))
                }
                ResultData.Complete(response.body())
            } catch (e: Exception) {
                ResultData.Error(e)
            }
        }

    fun streamGenerate(prompt: String, model: String? = null): Flow<ResultData<String>> = flow {
        try {
            client.preparePost {
                url("$baseUrl/${EndPoints.aiGenerate()}")
                contentType(ContentType.Application.Json)
                getAuthToken()?.let { token ->
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(GenAiRequest(prompt, model, stream = true))
            }.execute { response ->
                try {
                    val channel = response.body<ByteReadChannel>()

                    while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
                        val line = channel.readUTF8Line()?.takeUnless { it.isEmpty() } ?: continue

                        // SSE format: "data: {...}"
                        val jsonData = if (line.startsWith("data: ")) {
                            line.removePrefix("data: ")
                        } else {
                            continue
                        }

                        val parsed: GenAiResponse = json.decodeFromString(jsonData)

                        if (parsed.error?.isNotEmpty() == true) {
                            emit(ResultData.Error(RuntimeException(parsed.error)))
                            break
                        }

                        if (parsed.response != null) {
                            emit(ResultData.Complete(parsed.response))
                        }

                        if (parsed.done) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    emit(ResultData.Error(e))
                }
            }
        } catch (e: Exception) {
            emit(ResultData.Error(e))
        }
    }

    suspend fun generateSummary(prompt: String, model: String? = null): ResultData<GenAiResponse> =
        generateMutex.withLock {
            try {
                val response = client.post("$baseUrl/${EndPoints.aiSummary()}") {
                    contentType(ContentType.Application.Json)
                    getAuthToken()?.let { token ->
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(GenAiRequest(prompt, model, stream = false))
                }
                ResultData.Complete(response.body())
            } catch (e: Exception) {
                ResultData.Error(e)
            }
        }

    fun streamSummary(prompt: String, model: String? = null): Flow<ResultData<String>> = flow {
        try {
            client.preparePost {
                url("$baseUrl/${EndPoints.aiSummary()}")
                contentType(ContentType.Application.Json)
                getAuthToken()?.let { token ->
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(GenAiRequest(prompt, model, stream = true))
            }.execute { response ->
                try {
                    val channel = response.body<ByteReadChannel>()

                    while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
                        val line = channel.readUTF8Line()?.takeUnless { it.isEmpty() } ?: continue

                        val jsonData = if (line.startsWith("data: ")) {
                            line.removePrefix("data: ")
                        } else {
                            continue
                        }

                        val parsed: GenAiResponse = json.decodeFromString(jsonData)

                        if (parsed.error?.isNotEmpty() == true) {
                            emit(ResultData.Error(RuntimeException(parsed.error)))
                            break
                        }

                        if (parsed.response != null) {
                            emit(ResultData.Complete(parsed.response))
                        }

                        if (parsed.done) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    emit(ResultData.Error(e))
                }
            }
        } catch (e: Exception) {
            emit(ResultData.Error(e))
        }
    }

    suspend fun generateActionPoints(prompt: String, model: String? = null): ResultData<GenAiResponse> =
        generateMutex.withLock {
            try {
                val response = client.post("$baseUrl/${EndPoints.aiActionPoints()}") {
                    contentType(ContentType.Application.Json)
                    getAuthToken()?.let { token ->
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(GenAiRequest(prompt, model, stream = false))
                }
                ResultData.Complete(response.body())
            } catch (e: Exception) {
                ResultData.Error(e)
            }
        }

    fun streamActionPoints(prompt: String, model: String? = null): Flow<ResultData<String>> = flow {
        try {
            client.preparePost {
                url("$baseUrl/${EndPoints.aiActionPoints()}")
                contentType(ContentType.Application.Json)
                getAuthToken()?.let { token ->
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(GenAiRequest(prompt, model, stream = true))
            }.execute { response ->
                try {
                    val channel = response.body<ByteReadChannel>()

                    while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
                        val line = channel.readUTF8Line()?.takeUnless { it.isEmpty() } ?: continue

                        val jsonData = if (line.startsWith("data: ")) {
                            line.removePrefix("data: ")
                        } else {
                            continue
                        }

                        val parsed: GenAiResponse = json.decodeFromString(jsonData)

                        if (parsed.error?.isNotEmpty() == true) {
                            emit(ResultData.Error(RuntimeException(parsed.error)))
                            break
                        }

                        if (parsed.response != null) {
                            emit(ResultData.Complete(parsed.response))
                        }

                        if (parsed.done) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    emit(ResultData.Error(e))
                }
            }
        } catch (e: Exception) {
            emit(ResultData.Error(e))
        }
    }

    suspend fun generateFaq(prompt: String, model: String? = null): ResultData<GenAiResponse> =
        generateMutex.withLock {
            try {
                val response = client.post("$baseUrl/${EndPoints.aiFaq()}") {
                    contentType(ContentType.Application.Json)
                    getAuthToken()?.let { token ->
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(GenAiRequest(prompt, model, stream = false))
                }
                ResultData.Complete(response.body())
            } catch (e: Exception) {
                ResultData.Error(e)
            }
        }

    fun streamFaq(prompt: String, model: String? = null): Flow<ResultData<String>> = flow {
        try {
            client.preparePost {
                url("$baseUrl/${EndPoints.aiFaq()}")
                contentType(ContentType.Application.Json)
                getAuthToken()?.let { token ->
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(GenAiRequest(prompt, model, stream = true))
            }.execute { response ->
                try {
                    val channel = response.body<ByteReadChannel>()

                    while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
                        val line = channel.readUTF8Line()?.takeUnless { it.isEmpty() } ?: continue

                        val jsonData = if (line.startsWith("data: ")) {
                            line.removePrefix("data: ")
                        } else {
                            continue
                        }

                        val parsed: GenAiResponse = json.decodeFromString(jsonData)

                        if (parsed.error?.isNotEmpty() == true) {
                            emit(ResultData.Error(RuntimeException(parsed.error)))
                            break
                        }

                        if (parsed.response != null) {
                            emit(ResultData.Complete(parsed.response))
                        }

                        if (parsed.done) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    emit(ResultData.Error(e))
                }
            }
        } catch (e: Exception) {
            emit(ResultData.Error(e))
        }
    }

    suspend fun generateTags(prompt: String, model: String? = null): ResultData<GenAiResponse> =
        generateMutex.withLock {
            try {
                val response = client.post("$baseUrl/${EndPoints.aiTags()}") {
                    contentType(ContentType.Application.Json)
                    getAuthToken()?.let { token ->
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(GenAiRequest(prompt, model, stream = false))
                }
                ResultData.Complete(response.body())
            } catch (e: Exception) {
                ResultData.Error(e)
            }
        }

    fun streamTags(prompt: String, model: String? = null): Flow<ResultData<String>> = flow {
        try {
            client.preparePost {
                url("$baseUrl/${EndPoints.aiTags()}")
                contentType(ContentType.Application.Json)
                getAuthToken()?.let { token ->
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(GenAiRequest(prompt, model, stream = true))
            }.execute { response ->
                try {
                    val channel = response.body<ByteReadChannel>()

                    while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
                        val line = channel.readUTF8Line()?.takeUnless { it.isEmpty() } ?: continue

                        val jsonData = if (line.startsWith("data: ")) {
                            line.removePrefix("data: ")
                        } else {
                            continue
                        }

                        val parsed: GenAiResponse = json.decodeFromString(jsonData)

                        if (parsed.error?.isNotEmpty() == true) {
                            emit(ResultData.Error(RuntimeException(parsed.error)))
                            break
                        }

                        if (parsed.response != null) {
                            emit(ResultData.Complete(parsed.response))
                        }

                        if (parsed.done) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    emit(ResultData.Error(e))
                }
            }
        } catch (e: Exception) {
            emit(ResultData.Error(e))
        }
    }
}
