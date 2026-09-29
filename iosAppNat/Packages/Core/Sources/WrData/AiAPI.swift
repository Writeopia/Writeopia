import Foundation
import WrModels
import WrNetwork

/// The AI commands of the editor, each backed by its own endpoint.
public enum AiCommand: String, CaseIterable, Identifiable, Sendable {
    case prompt
    case summary
    case actionPoints
    case faq
    case tags

    public var id: String { rawValue }

    var path: String {
        switch self {
        case .prompt: "api/ai/generate"
        case .summary: "api/ai/summary"
        case .actionPoints: "api/ai/action-points"
        case .faq: "api/ai/faq"
        case .tags: "api/ai/tags"
        }
    }
}

/// Who answers the AI commands.
public enum AiProvider: String, CaseIterable, Identifiable, Sendable {
    /// The on-device model of Apple Intelligence. Works in both spaces, offline too.
    case appleIntelligence
    /// The Writeopia backend (Gemini). Only in the open space.
    case cloud
    /// A model served on this machine by Ollama or llmman. Works in both spaces, offline too.
    case ollama

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .appleIntelligence: String(localized: "Apple Intelligence")
        case .cloud: String(localized: "Writeopia Cloud")
        case .ollama: String(localized: "Local AI (Ollama)")
        }
    }
}

/// Streams the answer of an AI command. Abstracted so the editor can be tested without the network.
public protocol AiStreaming: AnyObject {
    /// Each element is the whole answer received so far, not just the new part.
    func stream(_ command: AiCommand, prompt: String) -> AsyncThrowingStream<String, Error>
    /// Gets ready for a request that's likely to come, e.g. loads the on-device model.
    func prewarm()
}

public extension AiStreaming {
    func prewarm() {}
}

public struct AiStreamError: Error, Equatable {
    public let message: String

    public init(message: String) {
        self.message = message
    }
}

public final class AiAPI: AiStreaming {
    private let client: APIClient

    public init(client: APIClient) {
        self.client = client
    }

    public func usage() async throws -> AiUsage {
        try await client.get("api/ai/usage")
    }

    private struct GenerateBody: Encodable {
        let prompt: String
        let stream: Bool
    }

    private struct Chunk: Decodable {
        let response: String?
        let done: Bool?
        let error: String?
    }

    public func stream(_ command: AiCommand, prompt: String) -> AsyncThrowingStream<String, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    let lines = try await client.streamLines(.post, command.path, body: GenerateBody(prompt: prompt, stream: true))
                    for try await line in lines {
                        // Server-Sent Events: "data: {"response": "...", "done": false}"
                        guard line.hasPrefix("data:") else { continue }
                        let json = line.dropFirst("data:".count).trimmingCharacters(in: .whitespaces)
                        guard let chunk = try? JSONDecoder().decode(Chunk.self, from: Data(json.utf8)) else { continue }

                        if let error = chunk.error, !error.isEmpty {
                            throw AiStreamError(message: error)
                        }
                        if let response = chunk.response {
                            continuation.yield(response)
                        }
                        if chunk.done == true { break }
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}
