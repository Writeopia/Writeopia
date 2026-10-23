package io.writeopia.analytics

/**
 * Product event tracking. Only the web targets (js/wasmJs) actually send events to PostHog;
 * every other platform is a no-op until an SDK is wired up for it.
 */
expect object Analytics {
    fun capture(event: String, properties: Map<String, Any?> = emptyMap())

    fun identify(distinctId: String, properties: Map<String, Any?> = emptyMap())
}
