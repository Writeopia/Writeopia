package io.writeopia.api.core.auth.service

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.writeopia.api.core.auth.models.GoogleIdentity
import io.writeopia.api.core.auth.models.GoogleTokenException
import io.writeopia.connection.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

/** Verifies a Google ID token and extracts the claims we need. */
interface GoogleTokenVerifier {
    /** @throws GoogleTokenException when the token is not a valid Google ID token for one of our clients. */
    suspend fun verify(idToken: String): GoogleIdentity
}

/** Exchanges an OAuth authorization code for an ID token at Google's token endpoint. */
interface GoogleCodeExchanger {
    /** @return the `id_token` from Google's response. @throws GoogleTokenException on any failure. */
    suspend fun exchange(code: String, codeVerifier: String?, redirectUri: String, clientId: String): String
}

private const val GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs"
private const val GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
private val GOOGLE_ISSUERS = arrayOf("https://accounts.google.com", "accounts.google.com")

/**
 * Verifies the RS256 signature against Google's published JWKS, then checks issuer, audience
 * and expiry. The JWK provider caches keys, so the network is only hit on key rotation.
 */
class GoogleJwksTokenVerifier(
    private val allowedAudiences: List<String>,
    private val jwkProvider: JwkProvider = JwkProviderBuilder(URL(GOOGLE_JWKS_URL))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build(),
) : GoogleTokenVerifier {

    override suspend fun verify(idToken: String): GoogleIdentity = withContext(Dispatchers.IO) {
        try {
            val keyId = JWT.decode(idToken).keyId ?: throw GoogleTokenException("ID token has no key id")
            val publicKey = jwkProvider.get(keyId).publicKey as? RSAPublicKey
                ?: throw GoogleTokenException("Google key $keyId is not an RSA key")

            val jwt = JWT.require(Algorithm.RSA256(publicKey, null))
                .withIssuer(*GOOGLE_ISSUERS)
                .withAnyOfAudience(*allowedAudiences.toTypedArray())
                .acceptLeeway(60)
                .build()
                .verify(idToken)

            val subject = jwt.subject?.takeIf { it.isNotBlank() }
                ?: throw GoogleTokenException("ID token has no subject")
            val email = jwt.getClaim("email").asString()?.takeIf { it.isNotBlank() }
                ?: throw GoogleTokenException("ID token has no email")
            val verifiedClaim = jwt.getClaim("email_verified")
            val emailVerified = verifiedClaim.asBoolean() ?: verifiedClaim.asString()?.toBoolean() ?: false

            GoogleIdentity(
                subject = subject,
                email = email,
                emailVerified = emailVerified,
                name = jwt.getClaim("name").asString(),
                audience = jwt.audience.firstOrNull() ?: "",
            )
        } catch (e: GoogleTokenException) {
            throw e
        } catch (e: Exception) {
            throw GoogleTokenException("Invalid Google ID token: ${e.message}", e)
        }
    }
}

/**
 * Standard authorization-code exchange. Google requires the client secret even for PKCE flows of
 * "Desktop app" clients, which is why the exchange lives on the server instead of in the apps.
 */
class GoogleHttpCodeExchanger(
    private val clientSecrets: Map<String, String>,
    private val client: HttpClient = HttpClient(),
    private val tokenEndpoint: String = GOOGLE_TOKEN_ENDPOINT,
) : GoogleCodeExchanger {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun exchange(
        code: String,
        codeVerifier: String?,
        redirectUri: String,
        clientId: String
    ): String {
        val clientSecret = clientSecrets[clientId]
            ?: throw GoogleTokenException("No client secret configured for client $clientId")

        val response = try {
            client.submitForm(
                url = tokenEndpoint,
                formParameters = Parameters.build {
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("code", code)
                    append("redirect_uri", redirectUri)
                    append("grant_type", "authorization_code")
                    if (codeVerifier != null) append("code_verifier", codeVerifier)
                }
            )
        } catch (e: Exception) {
            throw GoogleTokenException("Could not reach Google token endpoint: ${e.message}", e)
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val description = runCatching {
                json.parseToJsonElement(body).jsonObject["error_description"]?.jsonPrimitive?.content
            }.getOrNull()
            logger.warn("Google code exchange failed with ${response.status}: ${description ?: body}")
            throw GoogleTokenException("Google code exchange failed: ${description ?: response.status}")
        }

        return runCatching {
            json.parseToJsonElement(body).jsonObject["id_token"]?.jsonPrimitive?.content
        }.getOrNull()
            ?: throw GoogleTokenException("Google token response has no id_token")
    }
}
