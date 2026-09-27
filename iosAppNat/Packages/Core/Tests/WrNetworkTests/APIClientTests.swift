import Foundation
import Testing
@testable import WrNetwork
import WrStorage

final class StubTransport: HTTPTransport {
    typealias Handler = (URLRequest) -> (Int, String)

    private(set) var requests: [URLRequest] = []
    private var handlers: [Handler]

    init(_ handlers: [Handler]) {
        self.handlers = handlers
    }

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        requests.append(request)
        let handler = handlers.count > 1 ? handlers.removeFirst() : handlers[0]
        let (status, body) = handler(request)
        let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!
        return (Data(body.utf8), response)
    }
}

struct Echo: Decodable, Equatable {
    let value: String
}

@Suite struct APIClientTests {
    let baseURL = URL(string: "https://example.com")!

    @Test func sendsBearerToken() async throws {
        let transport = StubTransport([{ _ in (200, #"{"value":"ok"}"#) }])
        let client = APIClient(transport: transport, tokenStore: InMemoryTokenStore(accessToken: "abc"), baseURL: baseURL)

        let echo: Echo = try await client.get("api/test")

        #expect(echo == Echo(value: "ok"))
        #expect(transport.requests[0].value(forHTTPHeaderField: "Authorization") == "Bearer abc")
        #expect(transport.requests[0].url?.absoluteString == "https://example.com/api/test")
    }

    @Test func refreshesTokenOnceAndRetries() async throws {
        let tokens = InMemoryTokenStore(accessToken: "old", refreshToken: "refresh-1")
        let transport = StubTransport([
            { _ in (401, "Token is not valid or has expired") },
            { _ in (200, #"{"accessToken":"new","refreshToken":"refresh-2"}"#) },
            { _ in (200, #"{"value":"retried"}"#) },
        ])
        let client = APIClient(transport: transport, tokenStore: tokens, baseURL: baseURL)

        let echo: Echo = try await client.get("api/test")

        #expect(echo.value == "retried")
        #expect(tokens.accessToken == "new")
        #expect(tokens.refreshToken == "refresh-2")
        #expect(transport.requests[1].url?.path() == "/api/auth/refresh")
        #expect(String(data: transport.requests[1].httpBody!, encoding: .utf8) == #"{"refreshToken":"refresh-1"}"#)
        #expect(transport.requests[2].value(forHTTPHeaderField: "Authorization") == "Bearer new")
    }

    @Test func expiresSessionWhenRefreshFails() async {
        let transport = StubTransport([
            { _ in (401, "") },
            { _ in (401, "Invalid or expired refresh token") },
        ])
        let client = APIClient(transport: transport, tokenStore: InMemoryTokenStore(accessToken: "old", refreshToken: "r"), baseURL: baseURL)
        var expired = false
        client.onSessionExpired = { expired = true }

        await #expect(throws: APIError.unauthorized) {
            let _: Echo = try await client.get("api/test")
        }
        #expect(expired)
    }

    @Test func unauthenticatedRequestsDontRefresh() async {
        let transport = StubTransport([{ _ in (401, "Invalid credentials") }])
        let client = APIClient(transport: transport, tokenStore: InMemoryTokenStore(refreshToken: "r"), baseURL: baseURL)

        await #expect(throws: APIError.unauthorized) {
            let _: Echo = try await client.send(.post, "api/auth/login", body: ["a": "b"], authenticated: false)
        }
        #expect(transport.requests.count == 1)
        #expect(transport.requests[0].value(forHTTPHeaderField: "Authorization") == nil)
    }

    @Test func readsServerMessages() {
        #expect(APIClient.serverMessage(Data(#"{"success":false,"message":"Invalid or expired code"}"#.utf8)) == "Invalid or expired code")
        #expect(APIClient.serverMessage(Data("User not found".utf8)) == "User not found")
        #expect(APIClient.serverMessage(Data("<html></html>".utf8)) == nil)
    }
}
