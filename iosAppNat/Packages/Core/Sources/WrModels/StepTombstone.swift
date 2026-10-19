import Foundation

/// A step deleted on this device that the backend has not been told about yet. Mirrors
/// `StoryStepTombstone` of the Compose app: while it is here no sync writes the step back, and
/// the next sync sends the deletion before anything else; once the backend has it, the
/// tombstone goes.
public struct StepTombstone: Codable, Equatable, Hashable, Sendable {
    public let stepId: String
    public let documentId: String
    public let workspaceId: String
    /// Epoch milliseconds.
    public let deletedAt: Int64

    public init(stepId: String, documentId: String, workspaceId: String, deletedAt: Int64) {
        self.stepId = stepId
        self.documentId = documentId
        self.workspaceId = workspaceId
        self.deletedAt = deletedAt
    }
}
