package io.writeopia.api.auth

import io.writeopia.databse.HikariCp
import io.writeopia.sql.WriteopiaDbBackend

/**
 * Test-specific persistence configuration that always uses embedded PostgreSQL.
 */
fun configureTestPersistence(): WriteopiaDbBackend {
    val driver = HikariCp.driver(debugMode = true)
    if (!HikariCp.isSchemaCreated()) {
        WriteopiaDbBackend.Schema.create(driver)
        HikariCp.markSchemaCreated()
    }
    return WriteopiaDbBackend(driver)
}
