package io.writeopia.claudecode

import kotlinx.coroutines.flow.StateFlow

/**
 * The connection Claude Code uses to reach the app: a local MCP server, off until the person
 * turns it on in the settings. Only the desktop app has one: elsewhere [controller] stays null
 * and the settings don't show it.
 */
object ClaudeCodeConnection {
    /** The same port as the native Mac app: one `claude mcp add` works with whichever runs. */
    const val PORT = 47615

    var controller: ClaudeCodeController? = null
}

interface ClaudeCodeController {
    val state: StateFlow<ClaudeCodeState>

    fun setEnabled(enabled: Boolean)
}

sealed interface ClaudeCodeState {
    data object Off : ClaudeCodeState

    data class Running(val port: Int) : ClaudeCodeState {
        /**
         * What the person runs once in a terminal: the Writeopia plugin of Claude Code (the tools
         * and /writeopia:plan), from the marketplace repository Writeopia/writeopia-claude.
         */
        val pluginCommand: String =
            "claude plugin install writeopia --marketplace Writeopia/writeopia-claude"
    }

    data class Failed(val reason: String) : ClaudeCodeState
}
