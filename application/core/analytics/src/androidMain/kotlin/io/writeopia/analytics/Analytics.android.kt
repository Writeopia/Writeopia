package io.writeopia.analytics

actual object Analytics {
    actual fun capture(event: String, properties: Map<String, Any?>) {
        // No-op: PostHog is only wired up for the web targets so far.
    }

    actual fun identify(distinctId: String, properties: Map<String, Any?>) {
        // No-op: PostHog is only wired up for the web targets so far.
    }
}
