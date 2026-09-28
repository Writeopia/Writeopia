import Foundation

public struct AiUsage: Codable, Equatable, Sendable {
    public let totalInputTokens: Int64
    public let totalOutputTokens: Int64
    public let totalTokens: Int64
    public let requestCount: Int64
    public let periodStart: Int64
    public let periodEnd: Int64
    public let quota: Int64

    public init(
        totalInputTokens: Int64,
        totalOutputTokens: Int64,
        totalTokens: Int64,
        requestCount: Int64,
        periodStart: Int64,
        periodEnd: Int64,
        quota: Int64
    ) {
        self.totalInputTokens = totalInputTokens
        self.totalOutputTokens = totalOutputTokens
        self.totalTokens = totalTokens
        self.requestCount = requestCount
        self.periodStart = periodStart
        self.periodEnd = periodEnd
        self.quota = quota
    }

    /// Fraction of the monthly quota already used, between 0 and 1.
    public var progress: Double {
        guard quota > 0 else { return 0 }
        return min(max(Double(totalTokens) / Double(quota), 0), 1)
    }
}
