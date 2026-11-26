package io.writeopia.core.folders.sync.cloud

/**
 * The user's own cloud storage account, as this platform signs in to it (Google Drive on desktop
 * and Android). Holds the tokens; [CloudFileStore] asks it for one on each request.
 */
interface CloudAccountConnector {

    /** Shown to the user, e.g. "Google Drive". */
    val providerName: String

    /** Has the user connected an account (and not disconnected it since)? */
    suspend fun isConnected(): Boolean

    /**
     * Asks the user to sign in and allow the app its storage, e.g. in the browser. Suspends until
     * they answer.
     */
    suspend fun connect(): Result<Unit>

    /** Forgets the account here, and tells the provider the app no longer has access. */
    suspend fun disconnect()

    /**
     * A valid access token; a new one when [forceRefresh]. Null when not connected or the user
     * took the access back, which needs [connect] again.
     */
    suspend fun accessToken(forceRefresh: Boolean): String?
}
