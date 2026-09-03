package io.writeopia.api.auth

import io.writeopia.databse.HikariCp
import io.writeopia.sql.WriteopiaDbBackend

private val debugMode: Boolean
    get() = System.getenv("WRITEOPIA_DEBUG_MODE")?.toBoolean() ?: false

fun configurePersistence(): WriteopiaDbBackend {
    val driver = HikariCp.driver(debugMode)
    if (debugMode) {
        HikariCp.initializeSchemaIfNeeded { WriteopiaDbBackend.Schema.create(it) }
    }
    return WriteopiaDbBackend(driver)
}
