package io.writeopia.auth.google

/**
 * Google OAuth client IDs used by the KMP clients. Client IDs are public by design, so they are
 * committed here. The backend keeps the matching list of allowed audiences.
 *
 * - [WEB_CLIENT_ID] is used by the web app (GIS code client) and by Android as the
 *   `serverClientId`, so the ID tokens Android receives carry the web client as audience.
 * - [DESKTOP_CLIENT_ID] is the "Desktop app" client used by the JVM loopback flow.
 */
object GoogleOAuthConfig {
    const val WEB_CLIENT_ID = "REPLACE_ME_WEB.apps.googleusercontent.com"
    const val DESKTOP_CLIENT_ID = "REPLACE_ME_DESKTOP.apps.googleusercontent.com"

    private const val PLACEHOLDER_PREFIX = "REPLACE_ME"

    /** False until real client IDs are pasted in; the sign-in button stays hidden meanwhile. */
    val isConfigured: Boolean
        get() = !WEB_CLIENT_ID.startsWith(PLACEHOLDER_PREFIX)

    val isDesktopConfigured: Boolean
        get() = !DESKTOP_CLIENT_ID.startsWith(PLACEHOLDER_PREFIX)
}
