package io.writeopia.di

import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.Logger

actual object ApiLogger : Logger {
    actual override fun log(message: String) {
        Logger.Companion.DEFAULT.log(message)
    }
}
