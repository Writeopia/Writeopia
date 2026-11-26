package io.writeopia.claudecode

import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Where Claude Code meets the screens: the editors on screen say they are there, so Claude can
 * write into the one that shows a document, and the app listens for the documents Claude asks
 * to open.
 */
object ClaudeCodeBridge {

    /** The editors on screen, the one shown last at the end (two while a transition runs). */
    private val _editors = MutableStateFlow<List<WriteopiaStateManager>>(emptyList())
    val editors: StateFlow<List<WriteopiaStateManager>> = _editors.asStateFlow()

    private val _openRequests = MutableSharedFlow<OpenRequest>(extraBufferCapacity = 4)

    /** The documents Claude asks to show. The app navigates to them. */
    val openRequests: SharedFlow<OpenRequest> = _openRequests.asSharedFlow()

    /** The editor the person looks at now, if any. */
    val current: WriteopiaStateManager? get() = _editors.value.lastOrNull()

    /** Draws a header picture for the document of an editor and keeps it; the URL, or why not. */
    fun interface HeaderPainter {
        suspend fun draw(instructions: String): Result<String>
    }

    private val painters = mutableMapOf<WriteopiaStateManager, HeaderPainter>()

    /** The editor on screen; [headerPainter] draws header pictures for its document, where the app can. */
    fun attach(manager: WriteopiaStateManager, headerPainter: HeaderPainter? = null) {
        if (headerPainter != null) painters[manager] = headerPainter
        _editors.update { editors -> editors - manager + manager }
    }

    fun detach(manager: WriteopiaStateManager) {
        painters.remove(manager)
        _editors.update { editors -> editors - manager }
    }

    fun headerPainter(manager: WriteopiaStateManager): HeaderPainter? = painters[manager]

    fun requestOpen(request: OpenRequest) {
        _openRequests.tryEmit(request)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _aiMode = MutableStateFlow(AiMode.OFF)

    /**
     * Whether Claude Code is at work: the app shows a banner with a button to stop it, the editor
     * takes no input and the writing agent stays quiet. On from the first call, it stays on
     * between calls (Claude may take a while to write the next section) and ends [idleTimeout]
     * after the last one, or when the person stops it.
     */
    val aiMode: StateFlow<AiMode> = _aiMode.asStateFlow()

    /** How long AI mode outlives the last call. */
    var idleTimeout: Duration = 60.seconds

    /** How long calls are refused after the person stopped Claude Code. */
    var stopCooldown: Duration = 60.seconds

    // Read by the server's threads, written by the screen's.
    @Volatile private var generation = 0L

    @Volatile private var stoppedGeneration = -1L

    @Volatile private var stoppedAt: TimeMark? = null

    @Volatile private var idleJob: Job? = null

    /**
     * A call starts writing. Returns its ticket, or null when the person stopped Claude Code a
     * moment ago: the call is refused.
     *
     * The ticket is taken and AI mode turned on before the stop is checked, and [stop] marks the
     * time before it reads the ticket: a stop that lands in between is either seen here (the call
     * is refused, AI mode goes off again) or sees this ticket and stops it. No lock is needed.
     */
    fun beginWriting(): Long? {
        idleJob?.cancel()
        val ticket = generation + 1
        generation = ticket
        _aiMode.value = AiMode.WRITING

        if (stoppedAt?.let { it.elapsedNow() < stopCooldown } == true) {
            if (stoppedGeneration < ticket) stoppedGeneration = ticket
            _aiMode.value = AiMode.OFF
            return null
        }
        return ticket
    }

    /** The call with [ticket] is done: AI mode waits for the next one, then ends. */
    fun endWriting(ticket: Long) {
        if (ticket != generation || isStopped(ticket)) return
        _aiMode.value = AiMode.WAITING
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(idleTimeout)
            _aiMode.value = AiMode.OFF
        }
    }

    /** Whether the person stopped the call with [ticket]. */
    fun isStopped(ticket: Long): Boolean = ticket <= stoppedGeneration

    /** The person stops Claude Code: the call in progress stops where it is. */
    fun stop() {
        // The time first, then the ticket: see [beginWriting].
        stoppedAt = TimeSource.Monotonic.markNow()
        stoppedGeneration = generation
        idleJob?.cancel()
        _aiMode.value = AiMode.OFF
    }

    /** Claude Code says it is done: AI mode ends now, without waiting for [idleTimeout]. */
    fun finish() {
        idleJob?.cancel()
        _aiMode.value = AiMode.OFF
    }

    /** Ends AI mode without refusing the next calls, like when the person turns the server off. */
    fun reset() {
        idleJob?.cancel()
        stoppedGeneration = generation
        stoppedAt = null
        _aiMode.value = AiMode.OFF
    }

    enum class AiMode {
        OFF,

        /** A call is typing into a document. */
        WRITING,

        /** Between calls: Claude Code is preparing the next one. */
        WAITING;

        val isOn: Boolean get() = this != OFF
    }

    sealed interface OpenRequest {
        /** A new, empty document in [folderId], or in the folder the person is in when null. */
        data class NewDocument(val folderId: String? = null) : OpenRequest

        data class Document(val id: String) : OpenRequest

        data class Folder(val id: String) : OpenRequest
    }
}
