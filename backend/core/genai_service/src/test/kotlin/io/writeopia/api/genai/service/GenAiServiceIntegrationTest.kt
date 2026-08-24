package io.writeopia.api.genai.service

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertFalse

/**
 * Integration tests for GenAiService.
 *
 * These tests verify that the service can be instantiated correctly and that
 * all required dependencies are available on the classpath.
 */
class GenAiServiceIntegrationTest {

    /**
     * Verifies that GenAiService can be instantiated.
     * This is a smoke test to ensure the class loads correctly.
     */
    @Test
    fun `GenAiService can be instantiated`() {
        val service = GenAiService(
            projectId = "",
            location = "us-central1",
            defaultModel = "gemini-2.0-flash"
        )

        assertNotNull(service)
    }

    /**
     * Verifies that the Google GenAI Client can be instantiated with required dependencies.
     *
     * This test ensures all transitive dependencies (OkHttp, etc.) are available on the classpath.
     *
     * In CI environments without Google Cloud credentials, the client may throw
     * IOException about missing credentials - this is expected and still proves
     * the dependencies loaded correctly.
     *
     * This module uses the Java SDK (com.google.genai:google-genai) which uses OkHttp
     * directly, avoiding Ktor version conflicts.
     */
    @Test
    fun `Google GenAI Client can be instantiated with required dependencies`() {
        // This test verifies that all transitive dependencies are available.
        // In CI without credentials, IOException is expected - that's fine,
        // it means the SDK classes loaded successfully.
        try {
            val client = com.google.genai.Client.builder()
                .project("test-project")
                .location("us-central1")
                .enterprise(true)
                .build()
            assertNotNull(client)
        } catch (e: java.io.IOException) {
            // Expected in CI - credentials not configured.
            // The important thing is we didn't get NoClassDefFoundError.
            assert(e.message?.contains("credentials") == true || e.message?.contains("credential") == true) {
                "Expected credentials error, got: ${e.message}"
            }
        } catch (e: IllegalStateException) {
            // Also acceptable - SDK may throw this when credentials are missing
            assert(e.message?.contains("credentials") == true || e.message?.contains("credential") == true) {
                "Expected credentials error, got: ${e.message}"
            }
        }
    }

    /**
     * Verifies that isAvailable returns false when projectId is blank.
     */
    @Test
    fun `isAvailable returns false when projectId is blank`() {
        val service = GenAiService(
            projectId = "",
            location = "us-central1",
            defaultModel = "gemini-2.0-flash"
        )

        assertFalse(service.isAvailable())
    }

    /**
     * Verifies that isAvailable returns false when projectId is only whitespace.
     */
    @Test
    fun `isAvailable returns false when projectId is whitespace`() {
        val service = GenAiService(
            projectId = "   ",
            location = "us-central1",
            defaultModel = "gemini-2.0-flash"
        )

        assertFalse(service.isAvailable())
    }
}
