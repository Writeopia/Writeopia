package io.writeopia.database.connection

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.writeopia.sdk.sql.WriteopiaDb
import java.util.Properties

object SqlDelightJdbcConnection {

    fun jdbcDriver(userName: String) = HikariCp.dataSource(userName).asJdbcDriver()

    fun inMemory() = JdbcSqliteDriver(
        JdbcSqliteDriver.IN_MEMORY,
        Properties(),
        WriteopiaDb.Schema.synchronous()
    )
}
