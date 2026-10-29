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

        // Capturing starts opted out: setup() runs before a space is chosen, and PostHog's own
        // automatic events (pageviews, autocapture) aren't gated by the isEnabled check below,
        // only the explicit capture()/identify() calls are.
        jsPosthogInit(POSTHOG_PROJECT_TOKEN)
        if (isEnabled) jsPosthogOptIn()
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
        if (!isSetUp) return
        if (enabled) jsPosthogOptIn() else jsPosthogOptOut()
    }
}

private fun jsPosthogInit(token: String): Unit =
    js("""
        posthog.init(token, {
            api_host: 'https://eu.i.posthog.com',
            person_profiles: 'identified_only',
            opt_out_capturing_by_default: true
        })
    """)

private fun jsPosthogOptIn(): Unit =
    js("posthog.opt_in_capturing()")

private fun jsPosthogOptOut(): Unit =
    js("posthog.opt_out_capturing()")

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
