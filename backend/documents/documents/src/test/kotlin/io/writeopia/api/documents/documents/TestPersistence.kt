package io.writeopia.api.documents.documents

import io.writeopia.databse.HikariCp
import io.writeopia.databse.createWriteopiaSchema
import io.writeopia.sql.WriteopiaDbBackend

/**
 * Test-specific persistence configuration that uses embedded PostgreSQL.
 */
fun configureTestPersistence(): WriteopiaDbBackend {
    val driver = HikariCp.driver(debugMode = true)
    HikariCp.initializeSchemaIfNeeded(::createWriteopiaSchema)
    return WriteopiaDbBackend(driver)
}
