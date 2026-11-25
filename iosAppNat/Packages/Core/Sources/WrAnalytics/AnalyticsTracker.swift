import Foundation

@MainActor
public protocol AnalyticsTracker {
    func capture(_ event: String, properties: [String: Any])
    func identify(_ distinctId: String, properties: [String: Any])

    /// The private space promises "no telemetry" in its own UI copy ("No request, no telemetry,
    /// no cloud"), so capture/identify must be a no-op there. `AppSession.setSpace` is the single
    /// place that calls this, whenever the space changes and once at cold start.
    func setEnabled(_ enabled: Bool)
}

public extension AnalyticsTracker {
    func capture(_ event: String) {
        capture(event, properties: [:])
    }
}
