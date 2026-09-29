import Foundation
import Testing
import WrData
import WrNetwork

private final class FakeTransport: HTTPTransport {
    var responses: [(Int, String)]
    private(set) var requests: [URLRequest] = []

    init(_ responses: [(Int, String)]) {
        self.responses = responses
    }

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        requests.append(request)
        let (status, body) = responses.removeFirst()
        return (Data(body.utf8), HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!)
    }
}

private final class FakeLines: LineStreamingTransport {
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

@Suite struct OllamaTests {
    private func api(_ transport: FakeTransport = FakeTransport([]), lines: FakeLines = FakeLines([])) -> OllamaAPI {
        OllamaAPI(baseURL: URL(string: "http://localhost:11434")!, transport: transport, lineTransport: lines)
    }

    @Test func listsTheInstalledModels() async throws {
        let transport = FakeTransport([(200, #"{"models":[{"name":"llama3.2:latest","model":"llama3.2:latest","size":2019393189},{"name":"x","model":"deepseek-r1:7b"}]}"#)])

        let models = try await api(transport).models()

        #expect(models.map(\.model) == ["llama3.2:latest", "deepseek-r1:7b"])
        #expect(models[0].size == 2_019_393_189)
        #expect(transport.requests[0].url?.path() == "/api/tags")
        #expect(transport.requests[0].httpMethod == "GET")
    }

    @Test func serverErrorsCarryTheMessage() async {
        let transport = FakeTransport([(404, #"{"error":"model 'x' not found"}"#)])

        await #expect(throws: OllamaError(message: "model 'x' not found")) {
            try await api(transport).models()
        }
    }

    @Test func generateAccumulatesThePieces() async throws {
        let lines = FakeLines([(200, [
            #"{"model":"m","response":"Hel","done":false}"#,
            "",
            #"{"model":"m","response":"lo","done":false}"#,
            #"{"model":"m","response":"","done":true}"#,
            #"{"model":"m","response":"ignored","done":false}"#,
        ])])

        var answers: [String] = []
        for try await answer in api(lines: lines).generate(model: "m", system: "sys", prompt: "hi") {
            answers.append(answer)
        }

        #expect(answers == ["Hel", "Hello", "Hello"])
        let request = lines.requests[0]
        #expect(request.url?.path() == "/api/generate")
        let body = try JSONSerialization.jsonObject(with: request.httpBody!) as! [String: Any]
        #expect(body["model"] as? String == "m")
        #expect(body["prompt"] as? String == "hi")
        #expect(body["system"] as? String == "sys")
        #expect(body["stream"] as? Bool == true)
    }

    @Test func errorLineFailsTheStream() async {
        let lines = FakeLines([(200, [#"{"error":"out of memory"}"#])])

        await #expect(throws: OllamaError(message: "out of memory")) {
            for try await _ in api(lines: lines).generate(model: "m", prompt: "hi") {}
        }
    }

    @Test func nonSuccessStatusFailsTheStream() async {
        let lines = FakeLines([(500, [#"{"error":"boom"}"#])])

        await #expect(throws: OllamaError(message: "boom")) {
            for try await _ in api(lines: lines).generate(model: "m", prompt: "hi") {}
        }
    }

    @Test func pullReportsProgressUntilSuccess() async throws {
        let lines = FakeLines([(200, [
            #"{"status":"pulling manifest"}"#,
            #"{"status":"pulling abc","digest":"abc","total":100,"completed":50}"#,
            #"{"status":"success"}"#,
            #"{"status":"ignored"}"#,
        ])])

        var progress: [OllamaPullProgress] = []
        for try await line in api(lines: lines).pull(model: " llama3.2 ") {
            progress.append(line)
        }

        #expect(progress.map(\.status) == ["pulling manifest", "pulling abc", "success"])
        #expect(progress[1].fraction == 0.5)
        #expect(progress[2].isDone)
        let body = try JSONSerialization.jsonObject(with: lines.requests[0].httpBody!) as! [String: Any]
        #expect(body["model"] as? String == "llama3.2")
    }

    @Test func ollamaAiWrapsThePromptLikeTheCloud() async throws {
        let lines = FakeLines([(200, [#"{"response":"ok","done":true}"#])])
        let ai = OllamaAi(api: api(lines: lines), model: "m")

        var answers: [String] = []
        for try await answer in ai.stream(.summary, prompt: "some text") {
            answers.append(answer)
        }

        #expect(answers == ["ok"])
        let body = try JSONSerialization.jsonObject(with: lines.requests[0].httpBody!) as! [String: Any]
        #expect(body["prompt"] as? String == AiPrompts.prompt(for: .summary, text: "some text"))
        #expect(body["system"] as? String == AiPrompts.instructions)
    }

    @Test func autoConfigDecodesTheBackendJsonAndFallsBack() throws {
        let json = #"{"ollamaUrl":"http://localhost:11434","llmmanUrl":"http://localhost:17434","modelTiers":[{"type":"LIGHT","modelName":"gemma4:e4b"},{"type":"HEAVY","modelName":"big"}],"defaultTierIndex":1}"#
        let config = try JSONDecoder().decode(LocalAiAutoConfig.self, from: Data(json.utf8))

        #expect(config.defaultTier?.modelName == "big")
        #expect(config.modelTiers[0].type == .light)
        #expect(LocalAiAutoConfig.fallback.defaultTier?.type == .medium)
    }
}
