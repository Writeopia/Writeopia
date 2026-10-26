package io.writeopia.analytics.testing

import io.writeopia.analytics.AnalyticsTracker

class FakeAnalyticsTracker : AnalyticsTracker {

    data class CapturedEvent(val event: String, val properties: Map<String, Any?>)
    data class IdentifiedUser(val distinctId: String, val properties: Map<String, Any?>)

    val capturedEvents = mutableListOf<CapturedEvent>()
    val identifiedUsers = mutableListOf<IdentifiedUser>()

    override fun capture(event: String, properties: Map<String, Any?>) {
        capturedEvents += CapturedEvent(event, properties)
    }

    override fun identify(distinctId: String, properties: Map<String, Any?>) {
        identifiedUsers += IdentifiedUser(distinctId, properties)
    }
}
