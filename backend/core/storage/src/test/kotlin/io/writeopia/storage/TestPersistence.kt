package io.writeopia.storage

import io.writeopia.databse.HikariCp
import io.writeopia.databse.createWriteopiaSchema
import io.writeopia.sql.WriteopiaDbBackend

/** Embedded PostgreSQL with the whole schema, like the other backend tests. */
fun configureTestPersistence(): WriteopiaDbBackend {
    val driver = HikariCp.driver(debugMode = true)
    HikariCp.initializeSchemaIfNeeded(::createWriteopiaSchema)
    return WriteopiaDbBackend(driver)
}
