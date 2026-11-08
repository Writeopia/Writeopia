package io.writeopia.api.media

import io.writeopia.connection.logger
import io.writeopia.databse.HikariCp
import io.writeopia.databse.createWriteopiaSchema
import io.writeopia.sql.WriteopiaDbBackend

/**
 * The database the storage usage of the users is kept in. In debug mode an embedded Postgres
 * with a fresh schema. In production the service connects like the other microservices
 * (`JDBC_URL`, `DB_USER`, `DB_PASS`); until those are set, the service runs without a database
 * and uploads are not counted, which is logged loudly rather than refusing every upload.
 */
fun configurePersistence(debugMode: Boolean): WriteopiaDbBackend? {
    if (!debugMode && System.getenv("JDBC_URL") == null && System.getenv("DB_HOST") == null) {
        logger.error("Media service has no database (JDBC_URL/DB_HOST unset): storage usage is NOT tracked")
        return null
    }

    val driver = HikariCp.driver(debugMode)
    if (debugMode) {
        HikariCp.initializeSchemaIfNeeded(::createWriteopiaSchema)
    }
    return WriteopiaDbBackend(driver)
}
