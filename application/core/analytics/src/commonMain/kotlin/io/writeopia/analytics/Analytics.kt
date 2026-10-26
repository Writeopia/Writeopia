package io.writeopia.analytics

interface AnalyticsTracker {
    fun capture(event: String, properties: Map<String, Any?> = emptyMap())

    fun identify(distinctId: String, properties: Map<String, Any?> = emptyMap())
}

// Only the web targets (js/wasmJs) actually send events to PostHog; every other platform is a no-op.
expect object Analytics : AnalyticsTracker {
    override fun capture(event: String, properties: Map<String, Any?>)

    override fun identify(distinctId: String, properties: Map<String, Any?>)
}
