package io.writeopia.databse

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.writeopia.sql.WriteopiaDbBackend
import java.util.Properties

object SqlDelightJdbcConnection {

    fun jdbcDriver(userName: String) = HikariCp.dataSource(userName).asJdbcDriver()

    fun inMemory() = JdbcSqliteDriver(
        JdbcSqliteDriver.IN_MEMORY,
        Properties(),
        WriteopiaDbBackend.Schema.synchronous()
    )
}
