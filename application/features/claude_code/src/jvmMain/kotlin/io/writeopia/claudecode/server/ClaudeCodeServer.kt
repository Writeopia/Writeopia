package io.writeopia.claudecode.server

import io.writeopia.claudecode.LiveTags
import io.writeopia.claudecode.LiveFolders
import io.writeopia.claudecode.ClaudeCodeBridge
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.writeopia.claudecode.ClaudeCodeConnection
import io.writeopia.claudecode.ClaudeCodeController
import io.writeopia.claudecode.ClaudeCodeState
import io.writeopia.claudecode.LiveDocuments
import io.writeopia.claudecode.mcp.McpHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.prefs.Preferences

/**
 * The MCP server of the desktop app, for Claude Code: `POST /mcp` on 127.0.0.1 only, each request
 * carrying the secret shown in the settings as a bearer token. Requests from web pages (an
 * `Origin` that is not this machine) are refused, so a site can't drive it.
 */
class ClaudeCodeServer private constructor(
    private val handler: McpHandler,
    private val preferences: Preferences,
    private val port: Int,
) : ClaudeCodeController {

    private val _state = MutableStateFlow<ClaudeCodeState>(ClaudeCodeState.Off)
    override val state: StateFlow<ClaudeCodeState> = _state.asStateFlow()

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    override fun setEnabled(enabled: Boolean) {
        preferences.putBoolean(KEY_ENABLED, enabled)
        if (enabled) start() else stop()
    }

    override fun renewToken() {
        preferences.put(KEY_TOKEN, newToken())
        if (server != null) _state.value = ClaudeCodeState.Running(port, token())
    }

    private fun token(): String =
        preferences.get(KEY_TOKEN, null) ?: newToken().also { preferences.put(KEY_TOKEN, it) }

    private fun start() {
        if (server != null) return

        _state.value = try {
            server = embeddedServer(CIO, host = HOST, port = port) {
                routing {
                    post("/mcp") { handle(call) }
                    // No event stream from the server, and no sessions to end.
                    get("/mcp") { call.respond(HttpStatusCode.MethodNotAllowed) }
                    delete("/mcp") { call.respond(HttpStatusCode.MethodNotAllowed) }
                }
            }.start(wait = false)
            ClaudeCodeState.Running(port, token())
        } catch (e: Exception) {
            server = null
            ClaudeCodeState.Failed(
                "The port $port is taken (${e.message}). Is the other Writeopia app running with Claude Code on?"
            )
        }
    }

    private fun stop() {
        ClaudeCodeBridge.reset()
        server?.stop(gracePeriodMillis = 0, timeoutMillis = 500)
        server = null
        _state.value = ClaudeCodeState.Off
    }

    private suspend fun handle(call: ApplicationCall) {
        val origin = call.request.headers[HttpHeaders.Origin]
        if (origin != null && !isLocalOrigin(origin)) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }

        val bearer = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
        if (bearer == null || !sameSecret(bearer, token())) {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }

        when (val answer = handler.handle(call.receiveText())) {
            null -> call.respond(HttpStatusCode.Accepted)
            else -> call.respondText(answer, ContentType.Application.Json)
        }
    }

    companion object {
        private const val HOST = "127.0.0.1"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TOKEN = "token"

        /**
         * Makes the server of the app and hands it to the settings; it starts right away when
         * the person had turned it on.
         */
        fun install(appVersion: String, folders: LiveFolders? = null, tags: LiveTags? = null) {
            if (ClaudeCodeConnection.controller != null) return

            val server = ClaudeCodeServer(
                handler = McpHandler(LiveDocuments(folders = folders, tags = tags), serverVersion = appVersion),
                preferences = Preferences.userRoot().node("io/writeopia/claudecode"),
                port = ClaudeCodeConnection.PORT,
            )
            ClaudeCodeConnection.controller = server
            if (server.preferences.getBoolean(KEY_ENABLED, false)) server.start()
        }

        internal fun isLocalOrigin(origin: String): Boolean =
            Regex("""^https?://(localhost|127\.0\.0\.1|\[::1])(:\d+)?$""").matches(origin)

        private fun sameSecret(given: String, expected: String): Boolean =
            MessageDigest.isEqual(given.toByteArray(), expected.toByteArray())

        private fun newToken(): String {
            val bytes = ByteArray(24)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
