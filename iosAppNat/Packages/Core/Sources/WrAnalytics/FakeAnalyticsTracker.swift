import Foundation

/// Records captured events in memory instead of sending them, for tests.
@MainActor
public final class FakeAnalyticsTracker: AnalyticsTracker {
    public struct CapturedEvent: Equatable {
        public let event: String
        public let properties: [String: AnyHashable]

        public init(event: String, properties: [String: AnyHashable]) {
            self.event = event
            self.properties = properties
        }
    }

    public private(set) var capturedEvents: [CapturedEvent] = []
    public private(set) var identifiedUsers: [(distinctId: String, properties: [String: AnyHashable])] = []
    /// Tests assert on `capturedEvents` regardless of this: it just mirrors what was set.
    public private(set) var isEnabled = true

    public init() {}

    public func capture(_ event: String, properties: [String: Any]) {
        capturedEvents.append(CapturedEvent(event: event, properties: properties.compactMapValues { $0 as? AnyHashable }))
    }

    public func identify(_ distinctId: String, properties: [String: Any]) {
        identifiedUsers.append((distinctId, properties.compactMapValues { $0 as? AnyHashable }))
    }

    public func setEnabled(_ enabled: Bool) {
        isEnabled = enabled
    }
}
