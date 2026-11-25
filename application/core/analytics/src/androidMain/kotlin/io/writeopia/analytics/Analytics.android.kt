package io.writeopia.analytics

import android.content.Context
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

actual object Analytics : AnalyticsTracker {
    private var isSetUp = false

    // Off until the app confirms the user is in the open/online space: the private space
    // promises "no telemetry" in its own UI copy.
    private var isEnabled = false

    /** Starts the PostHog SDK. Call once, from Application.onCreate(). */
    fun setup(context: Context) {
        if (isSetUp) return
        isSetUp = true

        // Capturing starts opted out, and the SDK's own automatic events (lifecycle, screen
        // views, deep links) are disabled outright: setup() runs before a space is chosen, and
        // none of that is gated by the isEnabled check in capture()/identify() below.
        val config = PostHogAndroidConfig(
            projectToken = POSTHOG_PROJECT_TOKEN,
            host = "https://eu.i.posthog.com",
            captureApplicationLifecycleEvents = false,
            captureDeepLinks = false,
            captureScreenViews = false
        ).apply {
            optOut = true
        }
        PostHogAndroid.setup(context, config)
        if (isEnabled) PostHog.optIn()
    }

    actual override fun capture(event: String, properties: Map<String, Any?>) {
        if (!isEnabled) return
        PostHog.capture(event = event, properties = properties.filterValuesNotNull())
    }

    actual override fun identify(distinctId: String, properties: Map<String, Any?>) {
        if (!isEnabled) return
        PostHog.identify(distinctId = distinctId, userProperties = properties.filterValuesNotNull())
    }

    actual override fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (!isSetUp) return
        if (enabled) PostHog.optIn() else PostHog.optOut()
    }
}

private fun Map<String, Any?>.filterValuesNotNull(): Map<String, Any> =
    entries.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
