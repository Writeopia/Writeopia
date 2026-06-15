package io.writeopia.databse

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

private val config = HikariConfig().apply {
    val dbHost = System.getenv("DB_HOST") ?: "localhost"
    val dbPort = System.getenv("DB_PORT") ?: "5432"
    val dbName = System.getenv("DB_NAME") ?: "writeopia"
    jdbcUrl = System.getenv("JDBC_URL") ?: "jdbc:postgresql://$dbHost:$dbPort/$dbName"
    username = System.getenv("DB_USER") ?: "postgres"
    password = System.getenv("DB_PASS") ?: "postgres"
    maximumPoolSize = 10
    isAutoCommit = true
    transactionIsolation = "TRANSACTION_REPEATABLE_READ"
}

private val dataSource = HikariDataSource(config)

private val driver = dataSource.asJdbcDriver()

object HikariCp {
    fun driver(): SqlDriver = driver
}
