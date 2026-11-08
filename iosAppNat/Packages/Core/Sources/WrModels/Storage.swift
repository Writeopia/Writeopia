import Foundation

/// How much media the user keeps on the backend, like `StorageUsageResponse` of the Kotlin app.
/// `version` is the version of the row the numbers were read from; it grows with every change.
public struct StorageUsage: Codable, Equatable, Sendable {
    public let usedBytes: Int64
    public let quotaBytes: Int64
    public let version: Int64

    public init(usedBytes: Int64, quotaBytes: Int64, version: Int64) {
        self.usedBytes = usedBytes
        self.quotaBytes = quotaBytes
        self.version = version
    }

    /// Fraction of the storage already used, between 0 and 1.
    public var progress: Double {
        guard quotaBytes > 0 else { return 0 }
        return min(max(Double(usedBytes) / Double(quotaBytes), 0), 1)
    }

    public var isFull: Bool { usedBytes >= quotaBytes }
}
