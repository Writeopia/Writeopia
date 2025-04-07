package io.writeopia.api.geteway

import io.writeopia.databse.HikariCp
import io.writeopia.sql.WriteopiaDbBackend

fun configurePersistence() =
    WriteopiaDbBackend(HikariCp.driver())
