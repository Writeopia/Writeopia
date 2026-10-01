import Foundation
import WrNetwork

/// A model installed in Ollama (or llmman, which serves the same API).
public struct OllamaModel: Decodable, Sendable, Equatable, Identifiable {
    public let name: String
    public let model: String
    public let size: Int64?

    public var id: String { model }

    public init(name: String, model: String, size: Int64? = nil) {
        self.name = name
        self.model = model
        self.size = size
    }
}

/// One line of the answer of `/api/pull`.
public struct OllamaPullProgress: Decodable, Sendable, Equatable {
    public let status: String?
    public let digest: String?
    public let total: Int64?
    public let completed: Int64?
    public let error: String?

    public var isDone: Bool { status == "success" }

    /// 0...1 while a layer is downloading; nil for the other statuses.
    public var fraction: Double? {
        guard let total, total > 0, let completed else { return nil }
        return min(1, Double(completed) / Double(total))
    }
}

public struct OllamaError: Error, Equatable, LocalizedError {
    public let message: String

    public init(message: String) {
        self.message = message
    }

    public var errorDescription: String? { message }
}

/// Client of the Ollama HTTP API, mirroring `LocalAiApi` of the Compose app. Talks to a server
/// on this machine, so it doesn't go through `APIClient` (backend URL, bearer token).
public final class OllamaAPI {
    public static let defaultURL = URL(string: "http://localhost:11434")!
    /// llmman serves the Ollama API on another port.
    public static let llmmanURL = URL(string: "http://localhost:17434")!

    /// A cold start of a big model can take minutes before the first token arrives.
    public static let session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 600
        configuration.timeoutIntervalForResource = 24 * 60 * 60
        return URLSession(configuration: configuration)
    }()

    public let baseURL: URL
    private let transport: HTTPTransport
    private let lineTransport: LineStreamingTransport
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    public init(
        baseURL: URL,
        transport: HTTPTransport = OllamaAPI.session,
        lineTransport: LineStreamingTransport = OllamaAPI.session
    ) {
        self.baseURL = baseURL
        self.transport = transport
        self.lineTransport = lineTransport
    }

    // MARK: - Models

    private struct ModelsResponse: Decodable {
        let models: [OllamaModel]
    }

    /// The installed models. Failing here means the server isn't running at `baseURL`.
    public func models() async throws -> [OllamaModel] {
        let data = try await data(for: request(.get, "api/tags"))
        return try decoder.decode(ModelsResponse.self, from: data).models
    }

    private struct ModelBody: Encodable {
        let model: String
    }

    public func delete(model: String) async throws {
        _ = try await data(for: try request(.delete, "api/delete", body: ModelBody(model: model.trimmingCharacters(in: .whitespaces))))
    }

    /// Downloads a model, reporting each progress line until `isDone`.
    public func pull(model: String) -> AsyncThrowingStream<OllamaPullProgress, Error> {
        streamLines(.post, "api/pull", body: ModelBody(model: model.trimmingCharacters(in: .whitespaces))) { (progress: OllamaPullProgress, continuation) in
            if let error = progress.error, !error.isEmpty {
                throw OllamaError(message: error)
            }
            continuation.yield(progress)
            return progress.isDone
        }
    }

    // MARK: - Generate

    private struct GenerateBody: Encodable {
        let model: String
        let prompt: String
        let system: String?
        let stream: Bool
    }

    private struct GenerateChunk: Decodable {
        let response: String?
        let done: Bool?
        let error: String?
    }

    /// Streams the answer of `prompt`. Ollama sends each new piece on its own line; every element
    /// here is the whole answer so far, like the cloud AI of the backend.
    public func generate(model: String, system: String? = nil, prompt: String) -> AsyncThrowingStream<String, Error> {
        var answer = ""
        return streamLines(.post, "api/generate", body: GenerateBody(model: model, prompt: prompt, system: system, stream: true)) { (chunk: GenerateChunk, continuation) in
            if let error = chunk.error, !error.isEmpty {
                throw OllamaError(message: error)
            }
            if let piece = chunk.response {
                answer += piece
                continuation.yield(answer)
            }
            return chunk.done == true
        }
    }

    // MARK: - Requests

    public enum Method: String {
        case get = "GET"
        case post = "POST"
        case delete = "DELETE"
    }

    private func request(_ method: Method, _ path: String) -> URLRequest {
        var request = URLRequest(url: baseURL.appending(path: path))
        request.httpMethod = method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        return request
    }

    private func request<Body: Encodable>(_ method: Method, _ path: String, body: Body) throws -> URLRequest {
        var request = request(method, path)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(body)
        return request
    }

    private func data(for request: URLRequest) async throws -> Data {
        let (data, response) = try await transport.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            throw OllamaError(message: Self.errorMessage(in: data, status: status))
        }
        return data
    }

    /// Reads an NDJSON answer line by line. `handle` yields what it wants and returns true when
    /// the stream is complete.
    private func streamLines<Body: Encodable, Line: Decodable, Element>(
        _ method: Method,
        _ path: String,
        body: Body,
        handle: @escaping (Line, AsyncThrowingStream<Element, Error>.Continuation) throws -> Bool
    ) -> AsyncThrowingStream<Element, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    let (status, lines) = try await lineTransport.lines(for: try request(method, path, body: body))
                    guard (200..<300).contains(status) else {
                        var errorBody = ""
                        for try await line in lines { errorBody += line }
                        throw OllamaError(message: Self.errorMessage(in: Data(errorBody.utf8), status: status))
                    }
                    for try await line in lines {
                        let trimmed = line.trimmingCharacters(in: .whitespaces)
                        guard !trimmed.isEmpty else { continue }
                        let decoded = try decoder.decode(Line.self, from: Data(trimmed.utf8))
                        if try handle(decoded, continuation) { break }
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    private struct ErrorBody: Decodable {
        let error: String?
    }

    private static func errorMessage(in data: Data, status: Int) -> String {
        if let body = try? JSONDecoder().decode(ErrorBody.self, from: data), let error = body.error, !error.isEmpty {
            return error
        }
        return "Local AI answered with status \(status)."
    }
}
