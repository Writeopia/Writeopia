package io.writeopia.api.genai.service

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for GenAiService.
 *
 * These tests focus on the service's behavior when GenAI is not configured
 * (i.e., when GOOGLE_CLOUD_PROJECT is not set).
 */
class GenAiServiceTest {

    private fun createUnconfiguredService() = GenAiService(
        projectId = "",
        location = "us-central1",
        defaultModel = "gemini-2.0-flash"
    )

    @Test
    fun `generate returns error response when service is not available`() = runTest {
        val service = createUnconfiguredService()

        val response = service.generate("Test prompt")

        assertNull(response.response)
        assertFalse(response.done)
        val error = response.error
        assertNotNull(error)
        assertTrue(error.contains("not configured"))
    }

    @Test
    fun `generateSummary returns error response when service is not available`() = runTest {
        val service = createUnconfiguredService()

        val response = service.generateSummary("Some text to summarize")

        assertNull(response.response)
        assertFalse(response.done)
        val error = response.error
        assertNotNull(error)
        assertTrue(error.contains("not configured"))
    }

    @Test
    fun `generateActionPoints returns error response when service is not available`() = runTest {
        val service = createUnconfiguredService()

        val response = service.generateActionPoints("Some text with action items")

        assertNull(response.response)
        assertFalse(response.done)
        val error = response.error
        assertNotNull(error)
        assertTrue(error.contains("not configured"))
    }

    @Test
    fun `generateFaq returns error response when service is not available`() = runTest {
        val service = createUnconfiguredService()

        val response = service.generateFaq("Some text for FAQ generation")

        assertNull(response.response)
        assertFalse(response.done)
        val error = response.error
        assertNotNull(error)
        assertTrue(error.contains("not configured"))
    }

    @Test
    fun `generateTags returns error response when service is not available`() = runTest {
        val service = createUnconfiguredService()

        val response = service.generateTags("Some text for tag generation")

        assertNull(response.response)
        assertFalse(response.done)
        val error = response.error
        assertNotNull(error)
        assertTrue(error.contains("not configured"))
    }

    @Test
    fun `streamGenerate emits error when service is not available`() = runTest {
        val service = createUnconfiguredService()

        val responses = service.streamGenerate("Test prompt").toList()

        assertEquals(1, responses.size)
        val response = responses.first()
        assertNull(response.response)
        assertFalse(response.done)
        val error = response.error
        assertNotNull(error)
        assertTrue(error.contains("not configured"))
    }

    @Test
    fun `streamSummary emits error when service is not available`() = runTest {
        val service = createUnconfiguredService()

        val responses = service.streamSummary("Some text").toList()

        assertEquals(1, responses.size)
        val response = responses.first()
        val error = response.error
        assertNotNull(error)
        assertTrue(error.contains("not configured"))
    }

    @Test
    fun `streamActionPoints emits error when service is not available`() = runTest {
        val service = createUnconfiguredService()

        val responses = service.streamActionPoints("Some text").toList()

        assertEquals(1, responses.size)
        val response = responses.first()
        val error = response.error
        assertNotNull(error)
        assertTrue(error.contains("not configured"))
    }

    @Test
    fun `streamFaq emits error when service is not available`() = runTest {
        val service = createUnconfiguredService()

        val responses = service.streamFaq("Some text").toList()

        assertEquals(1, responses.size)
        val response = responses.first()
        val error = response.error
        assertNotNull(error)
        assertTrue(error.contains("not configured"))
    }

    @Test
    fun `streamTags emits error when service is not available`() = runTest {
        val service = createUnconfiguredService()

        val responses = service.streamTags("Some text").toList()

        assertEquals(1, responses.size)
        val response = responses.first()
        val error = response.error
        assertNotNull(error)
        assertTrue(error.contains("not configured"))
    }

    @Test
    fun `isAvailable returns true when projectId is set`() {
        val service = GenAiService(
            projectId = "my-project-id",
            location = "us-central1",
            defaultModel = "gemini-2.0-flash"
        )

        assertTrue(service.isAvailable())
    }
}
