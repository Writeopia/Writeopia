package io.writeopia.api.genai.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for AiGenerateResponse serialization.
 */
class AiGenerateResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `AiGenerateResponse with response serializes correctly`() {
        val response = AiGenerateResponse(
            response = "Generated text",
            done = true,
            error = null
        )

        val serialized = json.encodeToString(AiGenerateResponse.serializer(), response)
        val deserialized = json.decodeFromString(AiGenerateResponse.serializer(), serialized)

        assertEquals("Generated text", deserialized.response)
        assertTrue(deserialized.done)
        assertNull(deserialized.error)
    }

    @Test
    fun `AiGenerateResponse with error serializes correctly`() {
        val response = AiGenerateResponse(
            response = null,
            done = false,
            error = "Something went wrong"
        )

        val serialized = json.encodeToString(AiGenerateResponse.serializer(), response)
        val deserialized = json.decodeFromString(AiGenerateResponse.serializer(), serialized)

        assertNull(deserialized.response)
        assertFalse(deserialized.done)
        assertEquals("Something went wrong", deserialized.error)
    }

    @Test
    fun `AiGenerateResponse default values work correctly`() {
        val response = AiGenerateResponse()

        assertNull(response.response)
        assertFalse(response.done)
        assertNull(response.error)
    }

    @Test
    fun `AiGenerateResponse deserializes from JSON string`() {
        val jsonString = """{"response":"Hello","done":true}"""

        val response = json.decodeFromString(AiGenerateResponse.serializer(), jsonString)

        assertEquals("Hello", response.response)
        assertTrue(response.done)
        assertNull(response.error)
    }

    @Test
    fun `AiGenerateResponse deserializes with missing optional fields`() {
        val jsonString = """{"done":false}"""

        val response = json.decodeFromString(AiGenerateResponse.serializer(), jsonString)

        assertNull(response.response)
        assertFalse(response.done)
        assertNull(response.error)
    }
}
