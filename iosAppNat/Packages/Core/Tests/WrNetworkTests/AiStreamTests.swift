import Foundation
import Testing
import WrData
@testable import WrNetwork
import WrStorage

final class FakeLineTransport: LineStreamingTransport {
    var responses: [(Int, [String])]
    private(set) var requests: [URLRequest] = []

    init(_ responses: [(Int, [String])]) {
        self.responses = responses
    }

    func lines(for request: URLRequest) async throws -> (status: Int, lines: AsyncThrowingStream<String, Error>) {
        requests.append(request)
        let (status, lines) = responses.removeFirst()
        return (status, AsyncThrowingStream { continuation in
            lines.forEach { continuation.yield($0) }
            continuation.finish()
        })
    }
}

@Suite struct AiStreamTests {
    private func api(_ lines: FakeLineTransport, transport: HTTPTransport = URLSession.shared) -> AiAPI {
        let client = APIClient(
            transport: transport,
            lineTransport: lines,
            tokenStore: InMemoryTokenStore(accessToken: "a", refreshToken: "r"),
            baseURL: URL(string: "https://x.io")!
        )
        return AiAPI(client: client)
    }

    @Test func parsesServerSentEvents() async throws {
        let lines = FakeLineTransport([(200, [
            #"data: {"response":"Hel","done":false}"#,
            "",
            #"data: {"response":"Hello","done":false}"#,
            #"data: {"done":true}"#,
            #"data: {"response":"ignored after done"}"#,
        ])])

        var answers: [String] = []
        for try await answer in api(lines).stream(.actionPoints, prompt: "text") {
            answers.append(answer)
        }

        #expect(answers == ["Hel", "Hello"])
        let request = lines.requests[0]
        #expect(request.url?.path() == "/api/ai/action-points")
        #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer a")
        let body = try JSONSerialization.jsonObject(with: request.httpBody!) as! [String: Any]
        #expect(body["prompt"] as? String == "text")
        #expect(body["stream"] as? Bool == true)
    }

    @Test func errorEventFailsTheStream() async {
        let lines = FakeLineTransport([(200, [#"data: {"error":"Model unavailable"}"#])])

        await #expect(throws: AiStreamError(message: "Model unavailable")) {
            for try await _ in api(lines).stream(.summary, prompt: "text") {}
        }
    }

    @Test func quotaExceededIsReported() async {
        let lines = FakeLineTransport([(429, [])])

        await #expect(throws: APIError.quotaExceeded) {
            for try await _ in api(lines).stream(.prompt, prompt: "text") {}
        }
    }
}
