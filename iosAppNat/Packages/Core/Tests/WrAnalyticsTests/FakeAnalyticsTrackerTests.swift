import Testing
@testable import WrAnalytics

@MainActor
@Suite struct FakeAnalyticsTrackerTests {
    @Test func recordsCapturedEvents() {
        let tracker = FakeAnalyticsTracker()

        tracker.capture("document_created", properties: ["document_id": "doc-1"])

        #expect(tracker.capturedEvents.count == 1)
        #expect(tracker.capturedEvents[0].event == "document_created")
        #expect(tracker.capturedEvents[0].properties["document_id"] as? String == "doc-1")
    }

    @Test func recordsIdentifiedUsers() {
        let tracker = FakeAnalyticsTracker()

        tracker.identify("user-1", properties: ["tier": "premium"])

        #expect(tracker.identifiedUsers.count == 1)
        #expect(tracker.identifiedUsers[0].distinctId == "user-1")
    }
}
