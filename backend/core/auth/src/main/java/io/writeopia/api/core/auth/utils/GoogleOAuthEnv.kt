package io.writeopia.api.core.auth.utils

/**
 * Google OAuth configuration for "Sign in with Google".
 *
 * Client IDs are public by design, so the known ones are committed here and can be overridden
 * (or extended) with the `GOOGLE_OAUTH_CLIENT_IDS` env var (comma separated). They are the
 * audiences accepted on Google ID tokens: Android and iOS clients request tokens for the web
 * client (`serverClientId`), the desktop loopback flow yields tokens for the desktop client.
 *
 * Client secrets are only needed to exchange authorization codes (desktop + web popup flows)
 * and come exclusively from `GOOGLE_OAUTH_CLIENT_SECRETS`, formatted as
 * `clientId:secret,clientId2:secret2`.
 */
object GoogleOAuthEnv {
    const val WEB_CLIENT_ID = "REPLACE_ME_WEB.apps.googleusercontent.com"
    const val DESKTOP_CLIENT_ID = "REPLACE_ME_DESKTOP.apps.googleusercontent.com"

    val DEFAULT_CLIENT_IDS: List<String> = listOf(WEB_CLIENT_ID, DESKTOP_CLIENT_ID)

    fun allowedClientIds(): List<String> =
        System.getenv("GOOGLE_OAUTH_CLIENT_IDS")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_CLIENT_IDS

    fun clientSecrets(): Map<String, String> = parseClientSecrets(System.getenv("GOOGLE_OAUTH_CLIENT_SECRETS"))

    internal fun parseClientSecrets(raw: String?): Map<String, String> =
        raw.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.contains(':') }
            .associate { entry ->
                val clientId = entry.substringBefore(':').trim()
                val secret = entry.substringAfter(':').trim()
                clientId to secret
            }
            .filterKeys { it.isNotEmpty() }
            .filterValues { it.isNotEmpty() }
}
