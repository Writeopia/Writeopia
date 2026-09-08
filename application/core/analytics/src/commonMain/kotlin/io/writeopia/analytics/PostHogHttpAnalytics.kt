package io.writeopia.analytics

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.random.Random

/**
 * Sends events straight to PostHog's capture HTTP API. Used on platforms where the
 * official PostHog SDK isn't available (JVM Desktop, iOS, Web) - Android uses the
 * native PostHog Android SDK instead.
 */
class PostHogHttpAnalytics(
    private val httpClient: HttpClient,
    private val apiKey: String = PostHogConfig.API_KEY,
    private val host: String = PostHogConfig.HOST
) : AnalyticsManager {

    private var distinctId: String = randomId()
    private var identified: Boolean = false

    override suspend fun identify(distinctId: String, properties: Map<String, Any?>) {
        this.distinctId = distinctId
        identified = true
        capture("\$identify", properties)
    }

    override suspend fun track(event: String, properties: Map<String, Any?>) {
        capture(event, properties)
    }

    override fun reset() {
        distinctId = randomId()
        identified = false
    }

    private suspend fun capture(event: String, properties: Map<String, Any?>) {
        val body = buildJsonObject {
            put("api_key", apiKey)
            put("event", event)
            put("distinct_id", distinctId)
            putJsonObject("properties") {
                // Avoids creating a full PostHog person profile for anonymous events.
                if (!identified) put("\$process_person_profile", false)
                properties.forEach { (key, value) -> put(key, value.toJsonElement()) }
            }
        }

        runCatching {
            httpClient.post("$host/i/v0/e/") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    private fun randomId(): String = List(32) { Random.nextInt(16).toString(16) }.joinToString("")
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    else -> JsonPrimitive(toString())
}
