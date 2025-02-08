package io.writeopia.api.geteway

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.writeopia.sdk.sql.WriteopiaDb
import java.util.Properties

fun configurePersistence(url: String = "jdbc:sqlite:") =
    WriteopiaDb(JdbcSqliteDriver(url, Properties(), WriteopiaDb.Schema.synchronous()))
