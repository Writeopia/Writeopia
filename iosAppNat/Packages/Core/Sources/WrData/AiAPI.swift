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

    public func localAutoConfig() async throws -> LocalAiAutoConfig {
        try await client.get("api/ai/local-config", authenticated: false)
    }
}

/// Talks to an Ollama compatible server (e.g. Ollama running on a Mac in the same network).
public final class OllamaAPI {
    private let transport: HTTPTransport

    public init(transport: HTTPTransport = URLSession.shared) {
        self.transport = transport
    }

    private struct TagsResponse: Decodable {
        struct Model: Decodable {
            let name: String?
            let model: String?
        }

        let models: [Model]
    }

    /// Names of the models installed on the server at `baseURL`.
    public func models(baseURL: String) async throws -> [String] {
        let trimmed = baseURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed), url.scheme != nil else {
            throw APIError.badRequest("The URL is not valid.")
        }

        var request = URLRequest(url: url.appending(path: "api/tags"))
        request.timeoutInterval = 5

        let (data, response) = try await transport.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            throw APIError.unexpectedStatus(status)
        }

        guard let tags = try? JSONDecoder().decode(TagsResponse.self, from: data) else {
            throw APIError.decoding
        }
        return tags.models.compactMap { $0.model ?? $0.name }
    }
}
