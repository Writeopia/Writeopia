package io.writeopia.api.media

import io.writeopia.databse.HikariCp
import io.writeopia.databse.createWriteopiaSchema
import io.writeopia.sql.WriteopiaDbBackend

/**
 * The database the storage usage of the users is kept in: embedded Postgres with a fresh
 * schema in debug mode, the shared database (`JDBC_URL`, `DB_USER`, `DB_PASS`) in production.
 * It is required: every upload counts in the user's storage, so without it the service must
 * not start rather than store pictures nobody counts.
 */
fun configurePersistence(debugMode: Boolean): WriteopiaDbBackend {
    if (!debugMode && System.getenv("JDBC_URL") == null && System.getenv("DB_HOST") == null) {
        throw IllegalStateException(
            "The media service needs its database (JDBC_URL or DB_HOST): the storage usage of the " +
                "users is kept there and no upload is allowed without counting it."
        )
    }

    val driver = HikariCp.driver(debugMode)
    if (debugMode) {
        HikariCp.initializeSchemaIfNeeded(::createWriteopiaSchema)
    }
    return WriteopiaDbBackend(driver)
}
