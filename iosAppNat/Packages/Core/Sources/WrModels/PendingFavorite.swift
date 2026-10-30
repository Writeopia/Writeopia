import Foundation

/// The favorite of a document changed on this device that the backend has not been told about
/// yet. Mirrors `PendingFavorite` of the Compose app: while it is here no sync takes the
/// backend's favorite for the document, and the change is sent again until the backend has it.
/// One record per document, the latest change.
public struct PendingFavorite: Equatable, Hashable, Sendable {
    public let documentId: String
    public let workspaceId: String
    public let favorite: Bool
    /// Epoch milliseconds of the change: a send that began before a newer change doesn't drop
    /// the newer record when it ends.
    public let changedAt: Int64

    public init(documentId: String, workspaceId: String, favorite: Bool, changedAt: Int64) {
        self.documentId = documentId
        self.workspaceId = workspaceId
        self.favorite = favorite
        self.changedAt = changedAt
    }
}
