@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.core.manager

import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.coroutines.await
import org.w3c.fetch.RequestCredentials
import org.w3c.fetch.RequestInit
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Web-specific AuthRepository that uses HttpOnly cookies for token storage.
 *
 * Token security:
 * - Access and refresh tokens are stored in HttpOnly cookies (set by backend)
 * - Tokens cannot be accessed by JavaScript (XSS protection)
 * - A non-HttpOnly session metadata cookie is used for auth status checks
 *
 * Non-sensitive data (user info, workspace) remains in localStorage for convenience.
 */
internal class LocalStorageAuthRepository : AuthRepository {

    // Session-scoped storage for sensitive reset flow data (not persisted to localStorage)
    private var forgotPasswordEmail: String? = null
    private var forgotPasswordCode: String? = null

    override suspend fun getUser(): WriteopiaUser {
        val userId = localStorage.getItem(KEY_USER_ID) ?: return WriteopiaUser.disconnectedUser()
        val email = localStorage.getItem(KEY_USER_EMAIL) ?: ""
        val name = localStorage.getItem(KEY_USER_NAME) ?: ""
        val tierName = localStorage.getItem(KEY_USER_TIER) ?: Tier.FREE.name

        return WriteopiaUser(
            id = userId,
            email = email,
            name = name,
            tier = Tier.entries.find { it.name == tierName } ?: Tier.FREE
        )
    }

    override suspend fun isLoggedIn(): Boolean {
        // Check the session metadata cookie (not HttpOnly, accessible by JS)
        val sessionMeta = getCookie(COOKIE_SESSION_META)
        return sessionMeta != null && sessionMeta.isNotEmpty()
    }

    override suspend fun logout(): ResultData<Boolean> {
        // Call backend to revoke session and clear HttpOnly cookies
        try {
            val baseUrl = WriteopiaConnectionInjector.singleton().baseUrl()
            kotlinx.browser.window.fetch(
                "$baseUrl/api/auth/logout/web",
                RequestInit(
                    method = "POST",
                    credentials = RequestCredentials.INCLUDE
                )
            ).await()
        } catch (e: Exception) {
            // Continue with local cleanup even if backend call fails
            println("Web logout backend call failed: ${e.message}")
        }

        // Clear local storage data
        localStorage.removeItem(KEY_USER_ID)
        localStorage.removeItem(KEY_USER_EMAIL)
        localStorage.removeItem(KEY_USER_NAME)
        localStorage.removeItem(KEY_USER_TIER)
        localStorage.removeItem(KEY_USER_SELECTED)
        localStorage.removeItem(KEY_WORKSPACE_ID)
        localStorage.removeItem(KEY_WORKSPACE_USER_ID)
        localStorage.removeItem(KEY_WORKSPACE_NAME)
        localStorage.removeItem(KEY_WORKSPACE_LAST_SYNC)
        localStorage.removeItem(KEY_WORKSPACE_LAST_EVENT_SYNC)
        localStorage.removeItem(KEY_WORKSPACE_SELECTED)
        localStorage.removeItem(KEY_WORKSPACE_ROLE)
        clearForgotPasswordData()
        clearPendingConfirmationEmail()

        return ResultData.Complete(true)
    }

    override suspend fun saveUser(user: WriteopiaUser, selected: Boolean) {
        localStorage.setItem(KEY_USER_ID, user.id)
        localStorage.setItem(KEY_USER_EMAIL, user.email)
        localStorage.setItem(KEY_USER_NAME, user.name)
        localStorage.setItem(KEY_USER_TIER, user.tier.name)
        localStorage.setItem(KEY_USER_SELECTED, selected.toString())
    }

    /**
     * Returns null since tokens are stored in HttpOnly cookies.
     * The actual token is sent automatically by the browser with requests.
     */
    override suspend fun getAuthToken(): String? {
        // Tokens are in HttpOnly cookies, not accessible by JavaScript
        // Return null - Ktor client should be configured to include credentials
        return null
    }

    override suspend fun useOffline() {
        val user = getUser()
        saveUser(user.copy(id = WriteopiaUser.DISCONNECTED), true)
        unselectAllWorkspaces()
        saveWorkspace(Workspace.disconnectedWorkspace().copy(selected = true))
    }

    override suspend fun getWorkspace(): Workspace? {
        val id = localStorage.getItem(KEY_WORKSPACE_ID) ?: return null
        val userId = localStorage.getItem(KEY_WORKSPACE_USER_ID) ?: return null
        val name = localStorage.getItem(KEY_WORKSPACE_NAME) ?: ""
        val lastSyncMs = localStorage.getItem(KEY_WORKSPACE_LAST_SYNC)?.toLongOrNull() ?: 0L
        val lastEventSync = localStorage.getItem(KEY_WORKSPACE_LAST_EVENT_SYNC)?.toLongOrNull() ?: 0L
        val selected = localStorage.getItem(KEY_WORKSPACE_SELECTED)?.toBoolean() ?: false
        val role = localStorage.getItem(KEY_WORKSPACE_ROLE) ?: ""

        return Workspace(
            id = id,
            userId = userId,
            name = name,
            lastSync = Instant.fromEpochMilliseconds(lastSyncMs),
            lastEventSync = lastEventSync,
            selected = selected,
            role = role
        )
    }

    override suspend fun saveWorkspace(workspace: Workspace) {
        localStorage.setItem(KEY_WORKSPACE_ID, workspace.id)
        localStorage.setItem(KEY_WORKSPACE_USER_ID, workspace.userId)
        localStorage.setItem(KEY_WORKSPACE_NAME, workspace.name)
        localStorage.setItem(KEY_WORKSPACE_LAST_SYNC, workspace.lastSync.toEpochMilliseconds().toString())
        localStorage.setItem(KEY_WORKSPACE_LAST_EVENT_SYNC, workspace.lastEventSync.toString())
        localStorage.setItem(KEY_WORKSPACE_SELECTED, workspace.selected.toString())
        localStorage.setItem(KEY_WORKSPACE_ROLE, workspace.role)
    }

    override suspend fun unselectAllWorkspaces() {
        localStorage.setItem(KEY_WORKSPACE_SELECTED, false.toString())
    }

    override suspend fun updateLastEventSync(workspaceId: String, lastEventSync: Long) {
        localStorage.setItem(KEY_WORKSPACE_LAST_EVENT_SYNC, lastEventSync.toString())
    }

    override suspend fun unselectAllUsers() {
        localStorage.setItem(KEY_USER_SELECTED, false.toString())
    }

    override suspend fun savePendingConfirmationEmail(email: String) {
        localStorage.setItem(KEY_PENDING_CONFIRMATION_EMAIL, email)
    }

    override suspend fun getPendingConfirmationEmail(): String? =
        localStorage.getItem(KEY_PENDING_CONFIRMATION_EMAIL)

    override suspend fun clearPendingConfirmationEmail() {
        localStorage.removeItem(KEY_PENDING_CONFIRMATION_EMAIL)
    }

    override suspend fun saveForgotPasswordEmail(email: String) {
        forgotPasswordEmail = email
    }

    override suspend fun getForgotPasswordEmail(): String? = forgotPasswordEmail

    override suspend fun saveForgotPasswordCode(code: String) {
        forgotPasswordCode = code
    }

    override suspend fun getForgotPasswordCode(): String? = forgotPasswordCode

    override suspend fun clearForgotPasswordData() {
        forgotPasswordEmail = null
        forgotPasswordCode = null
    }

    /**
     * Tokens are managed by HttpOnly cookies set by the backend.
     * This method stores only non-sensitive metadata.
     */
    override suspend fun saveTokens(
        userId: String,
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long?
    ) {
        // Tokens are stored in HttpOnly cookies by the backend
        // We only store the expiry locally for quick checks
        expiresAt?.let { localStorage.setItem(KEY_TOKEN_EXPIRES_AT, it.toString()) }
            ?: localStorage.removeItem(KEY_TOKEN_EXPIRES_AT)
    }

    /**
     * Returns null since refresh tokens are in HttpOnly cookies.
     */
    override suspend fun getRefreshToken(): String? {
        // Refresh token is in HttpOnly cookie, not accessible by JavaScript
        return null
    }

    /**
     * Returns token data based on available information.
     * Actual tokens are not accessible due to HttpOnly cookies.
     */
    override suspend fun getTokenData(): TokenData? {
        // We can't access the actual tokens, but we can check session status
        val sessionMeta = getCookie(COOKIE_SESSION_META) ?: return null
        val parts = sessionMeta.split(":")
        val expiresAt = parts.getOrNull(1)?.toLongOrNull()

        // Return a placeholder TokenData - actual token is in HttpOnly cookie
        return TokenData(
            accessToken = "", // Not accessible
            refreshToken = null, // Not accessible
            accessTokenExpiresAt = expiresAt
        )
    }

    /**
     * Checks if the access token is expired using the session metadata cookie.
     */
    override suspend fun isAccessTokenExpired(): Boolean {
        // Check the session metadata cookie for expiry
        val sessionMeta = getCookie(COOKIE_SESSION_META) ?: return true
        val parts = sessionMeta.split(":")
        val expiresAt = parts.getOrNull(1)?.toLongOrNull() ?: return true
        return Clock.System.now().toEpochMilliseconds() >= expiresAt
    }

    /**
     * Local cleanup only - actual cookie clearing is done by backend.
     */
    override suspend fun clearTokens() {
        localStorage.removeItem(KEY_TOKEN_EXPIRES_AT)
        // HttpOnly cookies are cleared by calling /api/auth/logout/web
    }

    /**
     * Reads a cookie value by name.
     */
    private fun getCookie(name: String): String? {
        val cookies = document.cookie
        if (cookies.isEmpty()) return null

        return cookies.split(";")
            .map { it.trim() }
            .find { it.startsWith("$name=") }
            ?.substringAfter("=")
    }

    companion object {
        // Session cookie name (must match backend)
        private const val COOKIE_SESSION_META = "writeopia_session"

        // Local storage keys (for non-sensitive data only)
        private const val KEY_TOKEN_EXPIRES_AT = "writeopia_token_expires_at"
        private const val KEY_USER_ID = "writeopia_user_id"
        private const val KEY_USER_EMAIL = "writeopia_user_email"
        private const val KEY_USER_NAME = "writeopia_user_name"
        private const val KEY_USER_TIER = "writeopia_user_tier"
        private const val KEY_USER_SELECTED = "writeopia_user_selected"
        private const val KEY_WORKSPACE_ID = "writeopia_workspace_id"
        private const val KEY_WORKSPACE_USER_ID = "writeopia_workspace_user_id"
        private const val KEY_WORKSPACE_NAME = "writeopia_workspace_name"
        private const val KEY_WORKSPACE_LAST_SYNC = "writeopia_workspace_last_sync"
        private const val KEY_WORKSPACE_LAST_EVENT_SYNC = "writeopia_workspace_last_event_sync"
        private const val KEY_WORKSPACE_SELECTED = "writeopia_workspace_selected"
        private const val KEY_WORKSPACE_ROLE = "writeopia_workspace_role"
        private const val KEY_PENDING_CONFIRMATION_EMAIL = "writeopia_pending_confirmation_email"
    }
}
