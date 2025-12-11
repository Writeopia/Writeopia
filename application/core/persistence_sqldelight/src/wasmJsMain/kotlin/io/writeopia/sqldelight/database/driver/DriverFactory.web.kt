package io.writeopia.sqldelight.database.driver

import app.cash.sqldelight.db.SqlDriver

actual class DriverFactory {
    actual fun createDriver(url: String): SqlDriver = TODO("WASM NOT SUPPORTED!")
}
