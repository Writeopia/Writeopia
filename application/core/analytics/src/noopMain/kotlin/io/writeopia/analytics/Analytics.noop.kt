package io.writeopia.analytics

actual object Analytics : AnalyticsTracker {
    actual override fun capture(event: String, properties: Map<String, Any?>) {
        // No-op: events are only sent from the desktop (JVM) and Android apps.
    }

    actual override fun identify(distinctId: String, properties: Map<String, Any?>) {
        // No-op: events are only sent from the desktop (JVM) and Android apps.
    }

    actual override fun setEnabled(enabled: Boolean) {
        // No-op: nothing is ever sent from this target, so there is nothing to gate.
    }
}
