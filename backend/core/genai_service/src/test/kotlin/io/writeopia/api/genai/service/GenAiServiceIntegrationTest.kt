package io.writeopia.api.genai.service

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertFalse

/**
 * Integration tests for GenAiService.
 *
 * These tests verify that the service can be instantiated correctly and that
 * all required dependencies (like Ktor HttpTimeout) are available on the classpath.
 *
 * These tests would have caught the NoClassDefFoundError for HttpTimeout
 * that occurred when the google-genai-kotlin library tried to create a Ktor client.
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
     * Verifies that the Google GenAI Client can be instantiated with required Ktor dependencies.
     *
     * This test specifically catches the NoClassDefFoundError for HttpTimeout
     * that would occur if ktor-client-core is not on the classpath.
     *
     * The Client constructor internally creates a Ktor HttpClient with HttpTimeout,
     * so if the dependency is missing, this test will fail with NoClassDefFoundError.
     *
     * In CI environments without Google Cloud credentials, the client will throw
     * IOException about missing credentials - this is expected and still proves
     * the Ktor dependencies loaded correctly.
     *
     * This module uses Ktor 2.3.8 to match google-genai-kotlin:0.5.0's compiled dependencies.
     */
    @Test
    fun `Google GenAI Client can be instantiated with required Ktor dependencies`() {
        // This test verifies that all transitive dependencies are available.
        // If ktor-client-core is missing, this will throw NoClassDefFoundError.
        // In CI without credentials, IOException is expected - that's fine,
        // it means the Ktor classes loaded successfully.
        try {
            val client = com.google.genai.kotlin.Client(
                project = "test-project",
                location = "us-central1",
                enterprise = true
            )
            assertNotNull(client)
        } catch (e: java.io.IOException) {
            // Expected in CI - credentials not configured.
            // The important thing is we didn't get NoClassDefFoundError.
            assert(e.message?.contains("credentials") == true) {
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
