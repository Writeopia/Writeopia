package io.writeopia.api.core.email

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MailgunEmailSenderTest {
    @Test
    fun `sends through mailgun with basic auth and reports success on 200`() = runTest {
        var url = ""
        var auth = ""
        val sender = MailgunEmailSender(
            client = HttpClient(MockEngine { request ->
                url = request.url.toString()
                auth = request.headers["Authorization"].orEmpty()
                respond("", HttpStatusCode.OK)
            }),
            apiKey = "key-123",
            domain = "example.com",
            fromEmail = "noreply@writeopia.io",
        )

        assertTrue(sender.send("user@example.com", "Subject", "text", "<p>html</p>"))
        assertEquals("https://api.eu.mailgun.net/v3/example.com/messages", url)
        assertEquals("Basic YXBpOmtleS0xMjM=", auth)
    }

    @Test
    fun `reports failure when mailgun answers non-200`() = runTest {
        val sender = MailgunEmailSender(
            client = HttpClient(MockEngine { respond("", HttpStatusCode.InternalServerError) }),
            apiKey = "key-123",
            domain = "example.com",
        )

        assertFalse(sender.send("user@example.com", "Subject", "text", "<p>html</p>"))
    }

    @Test
    fun `returns true without a request when mailgun is not configured`() = runTest {
        val sender = MailgunEmailSender(
            client = HttpClient(MockEngine { error("must not call Mailgun without config") }),
            apiKey = null,
            domain = null,
        )

        assertTrue(sender.send("user@example.com", "Subject", "text", "<p>html</p>"))
    }
}
