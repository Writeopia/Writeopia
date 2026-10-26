package io.writeopia.analytics

import kotlin.js.json

@Suppress("UnsafeCastFromDynamic")
private external val posthog: dynamic

actual object Analytics : AnalyticsTracker {
    // Off until the app confirms the user is in the open/online space: the private space
    // promises "no telemetry" in its own UI copy.
    private var isEnabled = false

    actual override fun capture(event: String, properties: Map<String, Any?>) {
        if (!isEnabled) return
        posthog.capture(event, properties.toJsProps())
    }

    actual override fun identify(distinctId: String, properties: Map<String, Any?>) {
        if (!isEnabled) return
        posthog.identify(distinctId, properties.toJsProps())
    }

    actual override fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }
}

private fun Map<String, Any?>.toJsProps(): dynamic =
    json(*entries.map { (key, value) -> key to value }.toTypedArray())
