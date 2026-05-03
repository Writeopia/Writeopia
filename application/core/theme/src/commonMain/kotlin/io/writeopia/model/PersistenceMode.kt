package io.writeopia.model

/**
 * Defines how documents are persisted in the application.
 */
enum class PersistenceMode {
    /**
     * Documents are stored in a local database (SQLite/Room).
     * This is the default mode for offline-first usage.
     */
    LOCAL_DATABASE,

    /**
     * Documents are stored in memory only and synced with the backend server.
     * Changes sync as the user writes (with 2-second debounce), and
     * the whole workspace syncs when leaving the editor.
     * Requires an active internet connection.
     */
    MEMORY_WITH_SYNC
}
