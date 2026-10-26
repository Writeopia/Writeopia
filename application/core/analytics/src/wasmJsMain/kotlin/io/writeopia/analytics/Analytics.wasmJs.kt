package io.writeopia.analytics

actual object Analytics : AnalyticsTracker {
    // Off until the app confirms the user is in the open/online space: the private space
    // promises "no telemetry" in its own UI copy.
    private var isEnabled = false

    actual override fun capture(event: String, properties: Map<String, Any?>) {
        if (!isEnabled) return
        jsPosthogCapture(event, properties.toJsonObjectString())
    }

    actual override fun identify(distinctId: String, properties: Map<String, Any?>) {
        if (!isEnabled) return
        jsPosthogIdentify(distinctId, properties.toJsonObjectString())
    }

    actual override fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }
}

private fun jsPosthogCapture(event: String, propertiesJson: String): Unit =
    js("posthog.capture(event, JSON.parse(propertiesJson))")

private fun jsPosthogIdentify(distinctId: String, propertiesJson: String): Unit =
    js("posthog.identify(distinctId, JSON.parse(propertiesJson))")

private fun Map<String, Any?>.toJsonObjectString(): String =
    entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "${key.toJsonString()}:${value.toJsonValue()}"
    }

private fun Any?.toJsonValue(): String = when (this) {
    null -> "null"
    is Boolean -> toString()
    is Number -> toString()
    is String -> toJsonString()
    else -> toString().toJsonString()
}

private fun String.toJsonString(): String = buildString {
    append('"')
    for (char in this@toJsonString) {
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('"')
}
