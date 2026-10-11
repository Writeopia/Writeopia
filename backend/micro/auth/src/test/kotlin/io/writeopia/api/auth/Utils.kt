package io.writeopia.api.auth

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.writeopia.api.core.auth.repository.upsertEmailVerification
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.sql.WriteopiaDbBackend

fun ApplicationTestBuilder.defaultClient() = createClient {
    install(ContentNegotiation) {
        json(json = writeopiaJson)
    }
}

/**
 * Creates a client that stores and sends cookies automatically.
 * This is needed for testing HttpOnly cookie-based authentication.
 */
fun ApplicationTestBuilder.cookieClient() = createClient {
    install(ContentNegotiation) {
        json(json = writeopiaJson)
    }
    install(HttpCookies)
}

const val TEST_VERIFICATION_CODE = "123456"

/**
 * Builds a [RegisterRequest] for an email that already went through sign-up verification, by
 * seeding a valid code for it - registration requires a verified email.
 */
fun WriteopiaDbBackend.verifiedRegisterRequest(
    name: String,
    email: String,
    username: String,
    workspaceName: String,
    password: String,
): RegisterRequest {
    upsertEmailVerification(
        email = email.trim().lowercase(),
        code = TEST_VERIFICATION_CODE,
        codeExpiry = System.currentTimeMillis() + 15 * 60 * 1000L,
        // Old enough that a test asking for a new code isn't throttled.
        createdAt = 0L,
    )

    return RegisterRequest(
        name = name,
        email = email,
        username = username,
        workspaceName = workspaceName,
        password = password,
        verificationCode = TEST_VERIFICATION_CODE,
    )
}
