import Foundation
import WrStorage

/// Abstraction over `URLSession` so the client can be tested without the network.
public protocol HTTPTransport {
    func data(for request: URLRequest) async throws -> (Data, URLResponse)
}

extension URLSession: HTTPTransport {
    public func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        try await data(for: request, delegate: nil)
    }
}

/// Opens a streaming HTTP request and returns its status code and body lines, used for the
/// Server-Sent Events of the AI endpoints. Abstracted so it can be replaced in tests.
public protocol LineStreamingTransport {
    func lines(for request: URLRequest) async throws -> (status: Int, lines: AsyncThrowingStream<String, Error>)
}

extension URLSession: LineStreamingTransport {
    public func lines(for request: URLRequest) async throws -> (status: Int, lines: AsyncThrowingStream<String, Error>) {
        let (bytes, response) = try await bytes(for: request, delegate: nil)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        let stream = AsyncThrowingStream<String, Error> { continuation in
            let task = Task {
                do {
                    for try await line in bytes.lines {
                        continuation.yield(line)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
        return (status, stream)
    }
}

public struct EmptyBody: Codable, Sendable {
    public init() {}
}

/// JSON client for the Writeopia API gateway. Adds the bearer token to every request and, when
/// the access token expired, refreshes it once and retries the request.
public final class APIClient {
    public enum Method: String {
        case get = "GET"
        case post = "POST"
        case put = "PUT"
        case delete = "DELETE"
    }

    private let transport: HTTPTransport
    private let lineTransport: LineStreamingTransport
    private let tokenStore: TokenStore
    private let baseURL: URL
    private var refreshTask: Task<Bool, Never>?

    /// Called when the session can't be recovered, so the app can go back to the login screen.
    public var onSessionExpired: (() -> Void)?

    public init(
        transport: HTTPTransport = URLSession.shared,
        lineTransport: LineStreamingTransport = URLSession.shared,
        tokenStore: TokenStore,
        baseURL: URL = APIConfig.baseURL
    ) {
        self.transport = transport
        self.lineTransport = lineTransport
        self.tokenStore = tokenStore
        self.baseURL = baseURL
    }

    public static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        return decoder
    }()

    public static let encoder = JSONEncoder()

    // MARK: - Public API

    public func get<Response: Decodable>(
        _ path: String,
        query: [URLQueryItem] = [],
        authenticated: Bool = true
    ) async throws -> Response {
        let data = try await send(.get, path, query: query, body: Optional<EmptyBody>.none, authenticated: authenticated)
        return try decode(data)
    }

    public func send<Body: Encodable, Response: Decodable>(
        _ method: Method,
        _ path: String,
        body: Body,
        authenticated: Bool = true
    ) async throws -> Response {
        let data = try await send(method, path, query: [], body: body, authenticated: authenticated)
        return try decode(data)
    }

    /// Sends a request whose response body is not relevant.
    public func perform<Body: Encodable>(
        _ method: Method,
        _ path: String,
        body: Body? = Optional<EmptyBody>.none,
        authenticated: Bool = true
    ) async throws {
        _ = try await send(method, path, query: [], body: body, authenticated: authenticated)
    }

    /// Sends a request whose response is streamed line by line (Server-Sent Events). Like the
    /// other calls, an expired session is refreshed once before giving up.
    public func streamLines<Body: Encodable>(
        _ method: Method,
        _ path: String,
        body: Body
    ) async throws -> AsyncThrowingStream<String, Error> {
        var request = try makeRequest(method, path, query: [], body: body, authenticated: true)
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        var (status, lines) = try await lineTransport.lines(for: request)

        if status == 401 {
            guard await refreshSession() else {
                onSessionExpired?()
                throw APIError.unauthorized
            }
            request = try makeRequest(method, path, query: [], body: body, authenticated: true)
            request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
            (status, lines) = try await lineTransport.lines(for: request)
        }

        guard (200..<300).contains(status) else {
            if status == 401 { onSessionExpired?() }
            _ = try validate(Data(), status: status)
            throw APIError.unexpectedStatus(status)
        }
        return lines
    }

    // MARK: - Internals

    private func send<Body: Encodable>(
        _ method: Method,
        _ path: String,
        query: [URLQueryItem],
        body: Body?,
        authenticated: Bool
    ) async throws -> Data {
        let request = try makeRequest(method, path, query: query, body: body, authenticated: authenticated)
        let (data, status) = try await execute(request)

        if status == 401 && authenticated {
            guard await refreshSession() else {
                onSessionExpired?()
                throw APIError.unauthorized
            }

            let retry = try makeRequest(method, path, query: query, body: body, authenticated: authenticated)
            let (retryData, retryStatus) = try await execute(retry)
            if retryStatus == 401 {
                onSessionExpired?()
            }
            return try validate(retryData, status: retryStatus)
        }

        return try validate(data, status: status)
    }

    private func makeRequest<Body: Encodable>(
        _ method: Method,
        _ path: String,
        query: [URLQueryItem],
        body: Body?,
        authenticated: Bool
    ) throws -> URLRequest {
        var components = URLComponents(url: baseURL.appending(path: path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty {
            components.queryItems = query
        }

        var request = URLRequest(url: components.url!)
        request.httpMethod = method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        if authenticated, let accessToken = tokenStore.accessToken {
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        }

        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try Self.encoder.encode(body)
        }

        return request
    }

    private func execute(_ request: URLRequest) async throws -> (Data, Int) {
        let (data, response) = try await transport.data(for: request)
        return (data, (response as? HTTPURLResponse)?.statusCode ?? 0)
    }

    private func validate(_ data: Data, status: Int) throws -> Data {
        switch status {
        case 200..<300: return data
        case 400: throw APIError.badRequest(Self.serverMessage(data))
        case 401: throw APIError.unauthorized
        case 403: throw APIError.forbidden(Self.serverMessage(data))
        case 404: throw APIError.notFound
        case 409: throw APIError.conflict(Self.serverMessage(data))
        case 429: throw APIError.quotaExceeded
        default: throw APIError.unexpectedStatus(status)
        }
    }

    private func decode<Response: Decodable>(_ data: Data) throws -> Response {
        if Response.self == EmptyBody.self {
            return EmptyBody() as! Response
        }
        do {
            return try Self.decoder.decode(Response.self, from: data)
        } catch {
            #if DEBUG
            print("Decoding \(Response.self) failed: \(error)")
            #endif
            throw APIError.decoding
        }
    }

    /// Error bodies are either plain text or JSON with a `message` field, depending on the service.
    static func serverMessage(_ data: Data) -> String? {
        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let message = json["message"] as? String ?? json["error"] as? String {
            return message
        }

        guard let text = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !text.isEmpty,
              text.count < 200,
              !text.hasPrefix("<")
        else { return nil }
        return text
    }

    /// Refreshes the access token. Concurrent callers share the same refresh request.
    private func refreshSession() async -> Bool {
        if let refreshTask {
            return await refreshTask.value
        }

        let task = Task { [weak self] () -> Bool in
            guard let self, let refreshToken = tokenStore.refreshToken else { return false }

            do {
                let request = try makeRequest(
                    .post,
                    "api/auth/refresh",
                    query: [],
                    body: RefreshTokenRequest(refreshToken: refreshToken),
                    authenticated: false
                )
                let (data, status) = try await execute(request)
                guard (200..<300).contains(status) else { return false }

                let tokens = try Self.decoder.decode(RefreshTokenResponse.self, from: data)
                tokenStore.save(accessToken: tokens.accessToken, refreshToken: tokens.refreshToken ?? refreshToken)
                return true
            } catch {
                return false
            }
        }

        refreshTask = task
        let result = await task.value
        refreshTask = nil
        return result
    }
}

struct RefreshTokenRequest: Encodable {
    let refreshToken: String
}

struct RefreshTokenResponse: Decodable {
    let accessToken: String
    let refreshToken: String?
}
