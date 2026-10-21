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

    /// The commands of the "Quick actions" tab of the AI dialog: the ones that work on existing
    /// text. A free prompt is typed in the "Command" tab instead.
    public static let quickActions: [AiCommand] = [.summary, .actionPoints, .faq, .tags]

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
    /// What a free command does with the text, decided by a judge. Nil when this AI has no
    /// judge, so the answer is added to the document, which never loses text.
    func commandIntent(_ request: AiCommandRequest) async throws -> AiCommandIntent?
    /// The answer of a free command, written for the intent the request carries. Each element
    /// is the whole answer received so far.
    func streamCommand(_ request: AiCommandRequest) -> AsyncThrowingStream<String, Error>
}

public extension AiStreaming {
    func prewarm() {}

    func commandIntent(_ request: AiCommandRequest) async throws -> AiCommandIntent? { nil }

    func streamCommand(_ request: AiCommandRequest) -> AsyncThrowingStream<String, Error> {
        stream(.prompt, prompt: AiCommandPrompts.local(request))
    }
}

/// The prompt of a free command for a model without a judge (Apple Intelligence, Ollama), like
/// `localPrompt` of the Compose app.
public enum AiCommandPrompts {
    public static func local(_ request: AiCommandRequest) -> String {
        var prompt = ""
        if request.intent == .rewrite {
            prompt += "You are a writing assistant inside a text editor. The document below is in Markdown, one paragraph per line; a line like [[writeopia-block-3]] stands for a block you cannot see, keep every such line exactly as it is, in its place. Apply the writer's command and answer with the whole document again, in the same Markdown, keeping every line the command does not affect exactly as it is, without explaining what you did and without a code fence around the answer.\n"
        } else {
            prompt += "You are a writing assistant inside a text editor. Answer the writer's command with new content only, in the language of the document, without explaining what you did.\n"
        }
        if !request.documentTitle.isEmpty { prompt += "Document title: \(request.documentTitle)\n" }
        if !request.documentText.isEmpty { prompt += "Document:\n```\n\(request.documentText)\n```\n" }
        if !request.selectedText.isEmpty { prompt += "The writer selected these lines:\n```\n\(request.selectedText)\n```\n" }
        prompt += "Command: \(request.command)\n"
        return prompt
    }
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
        streamEvents(command.path, body: GenerateBody(prompt: prompt, stream: true))
    }

    /// Asks the backend (Jev) what a free command does with the text. Throws when the backend
    /// could not decide, so the caller falls back to adding the answer.
    public func commandIntent(_ request: AiCommandRequest) async throws -> AiCommandIntent? {
        var body = request
        body.intent = nil
        body.stream = false
        let response: AiCommandIntentResponse = try await client.send(.post, "api/ai/command-intent", body: body)
        guard let intent = response.intent else {
            throw AiStreamError(message: response.error ?? "The AI could not decide what to do")
        }
        return intent
    }

    public func streamCommand(_ request: AiCommandRequest) -> AsyncThrowingStream<String, Error> {
        var body = request
        body.stream = true
        return streamEvents("api/ai/command", body: body)
    }

    /// The Server-Sent Events answer of `path`: each element is the whole answer so far.
    private func streamEvents<Body: Encodable>(_ path: String, body: Body) -> AsyncThrowingStream<String, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    let lines = try await client.streamLines(.post, path, body: body)
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
