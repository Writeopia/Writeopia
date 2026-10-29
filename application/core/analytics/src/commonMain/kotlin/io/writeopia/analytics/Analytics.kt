package io.writeopia.analytics

interface AnalyticsTracker {
    fun capture(event: String, properties: Map<String, Any?> = emptyMap())

    fun identify(distinctId: String, properties: Map<String, Any?> = emptyMap())

    /**
     * The private/offline space promises "no telemetry" in its own UI copy, so capture/identify
     * must be a no-op there. Off by default; flipped on only once the open/online space is
     * positively confirmed.
     */
    fun setEnabled(enabled: Boolean)
}

// Only the web and Android targets actually send events to PostHog; every other platform is a no-op.
expect object Analytics : AnalyticsTracker {
    override fun capture(event: String, properties: Map<String, Any?>)

    override fun identify(distinctId: String, properties: Map<String, Any?>)

    override fun setEnabled(enabled: Boolean)
}
