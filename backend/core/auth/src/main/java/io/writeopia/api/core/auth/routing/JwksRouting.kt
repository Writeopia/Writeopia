package io.writeopia.api.core.auth.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.writeopia.api.core.auth.utils.JwtConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/**
 * JWKS (JSON Web Key Set) endpoint for ESPv2 JWT validation.
 *
 * ESPv2 requires JWT public keys to be served in JWKS format.
 * This endpoint converts the RSA public key to JWKS format and serves it.
 *
 * Endpoint: GET /.well-known/jwks.json
 */

@Serializable
data class JwksResponse(
    val keys: List<JwkKey>
)

@Serializable
data class JwkKey(
    val kty: String,      // Key type: "RSA"
    val alg: String,      // Algorithm: "RS256"
    val use: String,      // Key use: "sig" (signature)
    val kid: String,      // Key ID
    val n: String,        // Modulus (base64url encoded)
    val e: String         // Exponent (base64url encoded)
)

fun Routing.jwksRouting() {
    get("/.well-known/jwks.json") {
        try {
            val jwks = JwtConfig.getJwks()

            call.response.header(
                HttpHeaders.CacheControl,
                "public, max-age=3600" // Cache for 1 hour
            )

            call.respond(HttpStatusCode.OK, jwks)
        } catch (e: Exception) {
            call.application.environment.log.error("Failed to generate JWKS", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to generate JWKS: ${e.message}")
            )
        }
    }
}

/**
 * Extension to JwtConfig to generate JWKS from the public key
 */
fun JwtConfig.getJwks(): JwksResponse {
    val publicKey = getPublicKey()
    return JwksResponse(
        keys = listOf(convertRsaPublicKeyToJwk(publicKey))
    )
}

/**
 * Convert RSA public key to JWK (JSON Web Key) format
 */
private fun convertRsaPublicKeyToJwk(publicKey: RSAPublicKey): JwkKey {
    // Get the modulus (n) and exponent (e) from the public key
    val modulus = publicKey.modulus
    val exponent = publicKey.publicExponent

    // Convert to byte arrays
    val modulusBytes = modulus.toByteArray()
    val exponentBytes = exponent.toByteArray()

    // Remove leading zero byte if present (Java's BigInteger adds it for positive numbers)
    val modulusBytesClean = if (modulusBytes[0] == 0.toByte()) {
        modulusBytes.copyOfRange(1, modulusBytes.size)
    } else {
        modulusBytes
    }

    val exponentBytesClean = if (exponentBytes[0] == 0.toByte()) {
        exponentBytes.copyOfRange(1, exponentBytes.size)
    } else {
        exponentBytes
    }

    // Encode to base64url (without padding)
    val n = Base64.getUrlEncoder().withoutPadding().encodeToString(modulusBytesClean)
    val e = Base64.getUrlEncoder().withoutPadding().encodeToString(exponentBytesClean)

    return JwkKey(
        kty = "RSA",
        alg = "RS256",
        use = "sig",
        kid = "writeopia-1",  // Key ID
        n = n,
        e = e
    )
}

