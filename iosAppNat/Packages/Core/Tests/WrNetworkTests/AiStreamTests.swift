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

    @Test func acceptsJsonSoErrorsAreNot406() async throws {
        let lines = FakeLineTransport([(200, [#"data: {"response":"ok","done":true}"#])])
        for try await _ in api(lines).stream(.summary, prompt: "text") {}

        let accept = try #require(lines.requests.first?.value(forHTTPHeaderField: "Accept"))
        #expect(accept.contains("text/event-stream"))
        #expect(accept.contains("application/json"))
    }

    @Test func backendErrorMessagesAreShown() async {
        let lines = FakeLineTransport([(403, [#"{"response":null,"done":false,"error":"Cloud AI requires a premium subscription"}"#])])

        await #expect(throws: APIError.forbidden("Cloud AI requires a premium subscription")) {
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

final class PathTransport: HTTPTransport {
    private(set) var requests: [URLRequest] = []

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        requests.append(request)
        let body = request.url!.path().hasSuffix("/published") ? #"{"published":true}"# : "Document published"
        return (Data(body.utf8), HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!)
    }
}

@Suite struct PublishingAPITests {
    @Test func callsThePublishEndpoints() async throws {
        let transport = PathTransport()
        let client = APIClient(transport: transport, tokenStore: InMemoryTokenStore(accessToken: "a"), baseURL: URL(string: "https://x.io")!)
        let api = PublishingAPI(client: client, workspaceId: "w1")

        #expect(try await api.isPublished(documentId: "d1"))
        try await api.publish(documentId: "d1")
        try await api.unpublish(documentId: "d1")

        #expect(transport.requests.map { "\($0.httpMethod!) \($0.url!.path())" } == [
            "GET /api/docs/workspace/w1/document/d1/published",
            "POST /api/docs/workspace/w1/document/d1/publish",
            "POST /api/docs/workspace/w1/document/d1/unpublish",
        ])
    }
}

final class UploadTransport: HTTPTransport {
    private(set) var request: URLRequest?

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        self.request = request
        return (Data(#"{"imageUrl":"https://cdn/x.jpg"}"#.utf8), HTTPURLResponse(url: request.url!, statusCode: 201, httpVersion: nil, headerFields: nil)!)
    }
}

@Suite struct MediaAPITests {
    @Test func uploadsTheImageAsMultipartForm() async throws {
        let transport = UploadTransport()
        let client = APIClient(transport: transport, tokenStore: InMemoryTokenStore(accessToken: "a"), baseURL: URL(string: "https://x.io")!)

        let url = try await MediaAPI(client: client).uploadImage(Data([1, 2, 3]), fileName: "a.jpg", mimeType: "image/jpeg")

        #expect(url == "https://cdn/x.jpg")
        let request = try #require(transport.request)
        #expect(request.url?.path() == "/api/media/upload")
        #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer a")
        let contentType = try #require(request.value(forHTTPHeaderField: "Content-Type"))
        #expect(contentType.hasPrefix("multipart/form-data; boundary="))
        let body = String(decoding: try #require(request.httpBody), as: UTF8.self)
        #expect(body.contains(#"Content-Disposition: form-data; name="image"; filename="a.jpg""#))
        #expect(body.contains("Content-Type: image/jpeg"))
    }
}
