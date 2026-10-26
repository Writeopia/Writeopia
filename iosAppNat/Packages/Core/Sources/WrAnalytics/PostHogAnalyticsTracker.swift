import Foundation
import PostHog

@MainActor
public final class PostHogAnalyticsTracker: AnalyticsTracker {
    public static let shared = PostHogAnalyticsTracker()

    private var isSetUp = false
    /// Off until `AppSession` confirms the open space, so nothing is sent while "no telemetry"
    /// is still true (private space, or before the space is known at cold start).
    private var isEnabled = false

    private init() {}

    /// Starts the PostHog SDK. Call once, at app launch.
    public func setup() {
        guard !isSetUp else { return }
        isSetUp = true

        let config = PostHogConfig(
            projectToken: "phc_pTj77oub7kB8rTrmduW6CLr2pSApHzstj9fp63vRdVoE",
            host: "https://eu.i.posthog.com"
        )
        PostHogSDK.shared.setup(config)
    }

    public func capture(_ event: String, properties: [String: Any]) {
        guard isEnabled else { return }
        PostHogSDK.shared.capture(event, properties: properties)
    }

    public func identify(_ distinctId: String, properties: [String: Any]) {
        guard isEnabled else { return }
        PostHogSDK.shared.identify(distinctId, userProperties: properties)
    }

    public func setEnabled(_ enabled: Bool) {
        isEnabled = enabled
    }
}

/// Product event tracking, named to read the same way as the Kotlin `Analytics` object shared
/// code writes against.
public enum Analytics {
    public static let shared: AnalyticsTracker = PostHogAnalyticsTracker.shared
}
