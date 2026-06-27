package io.writeopia.databse

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

private fun createPostgresConfig() = HikariConfig().apply {
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

object HikariCp {
    private var dataSource: HikariDataSource? = null
    private var embeddedPostgres: EmbeddedPostgres? = null
    private var driver: SqlDriver? = null
    private var schemaCreated = false

    fun driver(debugMode: Boolean = false): SqlDriver {
        if (driver == null) {
            if (debugMode) {
                embeddedPostgres = EmbeddedPostgres.start()
                dataSource = HikariDataSource(HikariConfig().apply {
                    this.dataSource = embeddedPostgres!!.postgresDatabase
                    maximumPoolSize = 10
                    isAutoCommit = true
                })
            } else {
                dataSource = HikariDataSource(createPostgresConfig())
            }
            driver = dataSource!!.asJdbcDriver()
        }
        return driver!!
    }

    fun isSchemaCreated(): Boolean = schemaCreated

    fun markSchemaCreated() {
        schemaCreated = true
    }

    fun close() {
        dataSource?.close()
        embeddedPostgres?.close()
        dataSource = null
        embeddedPostgres = null
        driver = null
        schemaCreated = false
    }
}
