package io.writeopia

import android.util.Log
import io.ktor.client.plugins.logging.Logger

object AndroidLogger : Logger {
    override fun log(message: String) {
        Log.d("AndroidLogger", message)
    }
}
