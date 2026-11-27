package io.writeopia.claudecode.server

import io.ktor.server.application.install
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.writeopia.claudecode.LiveReader
import io.writeopia.claudecode.LiveSearch
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
import java.util.prefs.Preferences

/**
 * The MCP server of the desktop app, for Claude Code: `POST /mcp` on 127.0.0.1 only, while the
 * switch of the settings is on. No secret: what reaches 127.0.0.1 runs on this computer. Web pages
 * are refused: an `Origin` that is not this machine gets 403, and so does a `Host` that is not
 * 127.0.0.1/localhost (a site that points its own name at 127.0.0.1, DNS rebinding).
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

    private fun start() {
        if (server != null) return

        _state.value = try {
            server = embeddedServer(CIO, host = HOST, port = port) {
                // Counted on the bytes read, whatever Content-Length says.
                install(RequestBodyLimit) { bodyLimit { MAX_BODY_BYTES } }
                routing {
                    post("/mcp") { handle(call) }
                    // No event stream from the server, and no sessions to end.
                    get("/mcp") { call.respond(HttpStatusCode.MethodNotAllowed) }
                    delete("/mcp") { call.respond(HttpStatusCode.MethodNotAllowed) }
                }
            }.start(wait = false)
            ClaudeCodeState.Running(port)
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

        val host = call.request.headers[HttpHeaders.Host]
        if (host == null || !isLocalHost(host, port)) {
            call.respond(HttpStatusCode.Forbidden)
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
        private const val MAX_BODY_BYTES = 4L * 1024 * 1024

        /**
         * Makes the server of the app and hands it to the settings; it starts right away when
         * the person had turned it on.
         */
        fun install(
            appVersion: String,
            folders: LiveFolders? = null,
            tags: LiveTags? = null,
            search: LiveSearch? = null,
            reader: LiveReader? = null,
        ) {
            if (ClaudeCodeConnection.controller != null) return

            val server = ClaudeCodeServer(
                handler = McpHandler(
                    LiveDocuments(folders = folders, tags = tags, search = search, reader = reader),
                    serverVersion = appVersion
                ),
                preferences = Preferences.userRoot().node("io/writeopia/claudecode"),
                port = ClaudeCodeConnection.PORT,
            )
            ClaudeCodeConnection.controller = server
            if (server.preferences.getBoolean(KEY_ENABLED, false)) server.start()
        }

        internal fun isLocalOrigin(origin: String): Boolean =
            Regex("""^https?://(localhost|127\.0\.0\.1|\[::1])(:\d+)?$""").matches(origin)

        /** The name the request was sent to: this machine at [port], nothing else. */
        internal fun isLocalHost(host: String, port: Int): Boolean =
            host in setOf("127.0.0.1:$port", "localhost:$port", "[::1]:$port")
    }
}
