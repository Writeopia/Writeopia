package io.writeopia.analytics.testing

import io.writeopia.analytics.AnalyticsTracker

class FakeAnalyticsTracker : AnalyticsTracker {

    data class CapturedEvent(val event: String, val properties: Map<String, Any?>)
    data class IdentifiedUser(val distinctId: String, val properties: Map<String, Any?>)

    val capturedEvents = mutableListOf<CapturedEvent>()
    val identifiedUsers = mutableListOf<IdentifiedUser>()

    // Tests assert on capturedEvents regardless of this: it just mirrors what was set.
    var isEnabled = true
        private set

    override fun capture(event: String, properties: Map<String, Any?>) {
        capturedEvents += CapturedEvent(event, properties)
    }

    override fun identify(distinctId: String, properties: Map<String, Any?>) {
        identifiedUsers += IdentifiedUser(distinctId, properties)
    }

    override fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }
}
