import Foundation
import WrModels
import WrNetwork

public final class AiAPI {
    private let client: APIClient

    public init(client: APIClient) {
        self.client = client
    }

    public func usage() async throws -> AiUsage {
        try await client.get("api/ai/usage")
    }
}
