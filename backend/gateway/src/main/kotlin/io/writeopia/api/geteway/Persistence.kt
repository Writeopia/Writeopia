package io.writeopia.api.geteway

import io.writeopia.databse.SqlDelightJdbcConnection
import io.writeopia.sql.WriteopiaDbBackend

fun configurePersistence() =
    WriteopiaDbBackend(SqlDelightJdbcConnection.inMemory())
