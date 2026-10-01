import Foundation
import Testing
import WrData
import WrNetwork
import WrSession
import WrStorage

/// One fake Ollama per URL: which models it has, or nothing when it isn't "running".
private final class FakeOllama: HTTPTransport, LineStreamingTransport {
    var installed: [String: [String]]
    var pullLines: [String] = [#"{"status":"pulling","total":10,"completed":5}"#, #"{"status":"success"}"#]
    private(set) var pulled: [String] = []

    init(_ installed: [String: [String]]) {
        self.installed = installed
    }

    private func host(_ request: URLRequest) -> String {
        "\(request.url!.scheme!)://\(request.url!.host()!):\(request.url!.port!)"
    }

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        guard let models = installed[host(request)] else { throw URLError(.cannotConnectToHost) }
        let json = #"{"models":[\#(models.map { #"{"name":"\#($0)","model":"\#($0)"}"# }.joined(separator: ","))]}"#
        return (Data(json.utf8), HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!)
    }

    func lines(for request: URLRequest) async throws -> (status: Int, lines: AsyncThrowingStream<String, Error>) {
        let body = try JSONSerialization.jsonObject(with: request.httpBody!) as! [String: Any]
        let model = body["model"] as! String
        pulled.append(model)
        installed[host(request), default: []].append(model)
        let lines = pullLines
        return (200, AsyncThrowingStream { continuation in
            lines.forEach { continuation.yield($0) }
            continuation.finish()
        })
    }
}

private func controller(_ ollama: FakeOllama, url: URL = OllamaAPI.defaultURL, model: String? = nil, config: LocalAiAutoConfig = .fallback) -> (LocalAiConfigController, Recorder) {
    let recorder = Recorder()
    let controller = LocalAiConfigController(
        url: url,
        selectedModel: model,
        makeAPI: { OllamaAPI(baseURL: $0, transport: ollama, lineTransport: ollama) },
        autoConfig: { config }
    )
    controller.onChange = { url, model in recorder.changes.append((url, model)) }
    return (controller, recorder)
}

private final class Recorder {
    var changes: [(URL, String?)] = []
}

private func settle() async {
    for _ in 0..<50 {
        await Task.yield()
        try? await Task.sleep(for: .milliseconds(5))
    }
}

@Suite struct LocalAiConfigTests {
    @Test func loadsModelsAndPicksTheOnlyOne() async {
        let (controller, recorder) = controller(FakeOllama(["http://localhost:11434": ["llama3.2"]]))
        #expect(!controller.isConfigured)

        await controller.loadModels()

        #expect(controller.models == .loaded(["llama3.2"]))
        #expect(controller.selectedModel == "llama3.2")
        #expect(controller.isConfigured)
        #expect(recorder.changes.last?.1 == "llama3.2")
    }

    @Test func serverNotRunningIsReported() async {
        let (controller, _) = controller(FakeOllama([:]))

        await controller.loadModels()

        #expect(controller.models == .failed(String(localized: "Local AI isn't running at this address.")))
        #expect(!controller.isConfigured)
    }

    @Test func wizardDetectsProvidersAndDownloadsTheChosenTier() async {
        let ollama = FakeOllama(["http://localhost:17434": []])
        let (controller, recorder) = controller(ollama)

        controller.openWizard()
        await settle()

        guard case .selecting(let config, let providers) = controller.wizard else {
            Issue.record("wizard is \(controller.wizard)")
            return
        }
        #expect(config == .fallback)
        #expect(providers.map(\.name) == ["Ollama", "llmman"])
        #expect(providers.map(\.isAvailable) == [false, true])

        var started = false
        controller.confirmWizard(providerURL: providers[1].url, tier: config.defaultTier!) { started = true }
        #expect(started)
        #expect(controller.wizard == .closed)
        #expect(controller.url == OllamaAPI.llmmanURL)
        #expect(controller.selectedModel == "gpt-oss:20b")
        await settle()

        #expect(ollama.pulled == ["gpt-oss:20b"])
        #expect(controller.download == .idle)
        #expect(controller.models == .loaded(["gpt-oss:20b"]))
        #expect(controller.isConfigured)
        #expect(recorder.changes.last?.0 == OllamaAPI.llmmanURL)
        #expect(recorder.changes.last?.1 == "gpt-oss:20b")
    }

    @Test func wizardFailsWhenNothingRuns() async {
        let (controller, _) = controller(FakeOllama([:]))

        controller.openWizard()
        await settle()

        #expect(controller.wizard == .error(.noProviderDetected))
        controller.closeWizard()
        #expect(controller.wizard == .closed)
    }

    @Test func failedDownloadIsReported() async {
        let ollama = FakeOllama(["http://localhost:11434": []])
        ollama.pullLines = [#"{"error":"pull model manifest: file does not exist"}"#]
        let (controller, _) = controller(ollama)

        controller.download(model: "nope")
        await settle()

        #expect(controller.download == .failed("pull model manifest: file does not exist"))
    }

    @Test func sessionPicksTheLocalAiWhenConfigured() {
        let defaults = UserDefaults(suiteName: "tests.\(UUID().uuidString)")!
        let session = AppSession(
            tokenStore: InMemoryTokenStore(),
            preferences: Preferences(defaults: defaults),
            transport: FakeOllama([:]),
            isAppleIntelligenceAvailable: { false }
        )
        session.chooseOfflineSpace()
        session.aiProvider = .ollama
        #expect(session.aiClient == nil)

        session.localAiConfig.select(model: "llama3.2")

        #expect(session.localAiModel == "llama3.2")
        #expect(session.aiClient === session.ollamaAi)
        #expect(defaults.string(forKey: "wr.localAiModel") == "llama3.2")
    }
}
