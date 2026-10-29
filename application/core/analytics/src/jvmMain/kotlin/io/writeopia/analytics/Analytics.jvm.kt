package io.writeopia.analytics

import com.posthog.java.PostHog
import java.util.UUID

actual object Analytics : AnalyticsTracker {
    private var client: PostHog? = null

    // Off until the app confirms the user is in the open/online space: the private space
    // promises "no telemetry" in its own UI copy.
    private var isEnabled = false

    // The Java SDK has no built-in anonymous id (unlike the JS/mobile SDKs): one is kept for
    // the process, and replaced by the real id once `identify` is called.
    private var distinctId: String = UUID.randomUUID().toString()

    /** Starts the PostHog SDK. Call once, from the desktop app's entry point. */
    fun setup() {
        if (client != null) return
        client = PostHog.Builder(POSTHOG_PROJECT_TOKEN)
            .host("https://eu.i.posthog.com")
            .build()
        // The Java SDK batches captures and sends them after a delay; without this, events
        // captured right before the app closes can be lost when the process exits.
        Runtime.getRuntime().addShutdownHook(Thread { client?.shutdown() })
    }

    actual override fun capture(event: String, properties: Map<String, Any?>) {
        if (!isEnabled) return
        client?.capture(distinctId, event, properties.filterValuesNotNull())
    }

    actual override fun identify(distinctId: String, properties: Map<String, Any?>) {
        if (!isEnabled) return
        this.distinctId = distinctId
        client?.identify(distinctId, properties.filterValuesNotNull())
    }

    actual override fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }
}

private fun Map<String, Any?>.filterValuesNotNull(): Map<String, Any> =
    entries.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
