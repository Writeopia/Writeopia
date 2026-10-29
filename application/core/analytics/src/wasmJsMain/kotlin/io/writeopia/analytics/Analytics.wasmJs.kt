package io.writeopia.analytics

actual object Analytics : AnalyticsTracker {
    private var isSetUp = false

    // Off until the app confirms the user is in the open/online space: the private space
    // promises "no telemetry" in its own UI copy.
    private var isEnabled = false

    /** Starts the PostHog SDK. Call once, from the webapp's entry point. */
    fun setup() {
        if (isSetUp) return
        isSetUp = true

        jsPosthogInit(POSTHOG_PROJECT_TOKEN)
    }

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

private fun jsPosthogInit(token: String): Unit =
    js("posthog.init(token, { api_host: 'https://eu.i.posthog.com', person_profiles: 'identified_only' })")

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
