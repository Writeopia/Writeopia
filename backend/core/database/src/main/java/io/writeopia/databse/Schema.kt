package io.writeopia.databse

import app.cash.sqldelight.db.SqlDriver
import io.writeopia.databse.tombstone.StoryStepTombstoneStore
import io.writeopia.sql.WriteopiaDbBackend

/**
 * The whole schema on an empty database (debug mode and tests): what SQLDelight generates from
 * the `.sq` files, plus the tables that live outside it because its dialect can't express them
 * (the partitioned story step tombstones). Production and local Docker get the same from
 * `docker/postgres/init.sql` and the scripts in WriteopiaScripts2.
 */
fun createWriteopiaSchema(driver: SqlDriver) {
    WriteopiaDbBackend.Schema.create(driver)
    StoryStepTombstoneStore(driver).ensureSchema()
}
