package io.writeopia.analytics

import kotlin.js.json

@Suppress("UnsafeCastFromDynamic")
private external val posthog: dynamic

actual object Analytics : AnalyticsTracker {
    actual override fun capture(event: String, properties: Map<String, Any?>) {
        posthog.capture(event, properties.toJsProps())
    }

    actual override fun identify(distinctId: String, properties: Map<String, Any?>) {
        posthog.identify(distinctId, properties.toJsProps())
    }
}

private fun Map<String, Any?>.toJsProps(): dynamic =
    json(*entries.map { (key, value) -> key to value }.toTypedArray())
