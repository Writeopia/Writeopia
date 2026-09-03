package io.writeopia.api.auth

import io.writeopia.databse.HikariCp
import io.writeopia.sql.WriteopiaDbBackend

/**
 * Test-specific persistence configuration that always uses embedded PostgreSQL.
 */
fun configureTestPersistence(): WriteopiaDbBackend {
    val driver = HikariCp.driver(debugMode = true)
    HikariCp.initializeSchemaIfNeeded { WriteopiaDbBackend.Schema.create(it) }
    return WriteopiaDbBackend(driver)
}
