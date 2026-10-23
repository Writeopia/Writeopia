package io.writeopia.analytics

actual object Analytics {
    actual fun capture(event: String, properties: Map<String, Any?>) {
        jsPosthogCapture(event, properties.toJsonObjectString())
    }

    actual fun identify(distinctId: String, properties: Map<String, Any?>) {
        jsPosthogIdentify(distinctId, properties.toJsonObjectString())
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
