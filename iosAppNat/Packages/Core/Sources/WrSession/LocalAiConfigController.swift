import Foundation
import Observation
import WrData

/// Configuration of the local AI (Ollama or llmman), like `LocalAiConfigController` of the
/// Compose app: the server URL, the installed models, downloads and the "auto configure"
/// wizard. Owned by the session so a download keeps going after its screen is gone.
@Observable
public final class LocalAiConfigController {
    public enum Loadable<Value: Equatable>: Equatable {
        case idle
        case loading
        case loaded(Value)
        case failed(String)

        public var value: Value? {
            if case .loaded(let value) = self { return value }
            return nil
        }
    }

    public struct DownloadProgress: Equatable, Sendable {
        public let model: String
        public let status: String
        public let completed: Int64?
        public let total: Int64?

        /// 0...1 of the layer being downloaded; nil while preparing.
        public var fraction: Double? {
            guard let total, total > 0, let completed else { return nil }
            return min(1, Double(completed) / Double(total))
        }
    }

    public struct ProviderInfo: Equatable, Sendable, Identifiable {
        public let name: String
        public let url: URL
        public let isAvailable: Bool

        public var id: String { url.absoluteString }
    }

    public enum WizardError: Equatable, Sendable {
        case noProviderDetected
        case downloadFailed(String)
    }

    public enum WizardState: Equatable {
        case closed
        case detecting
        case selecting(LocalAiAutoConfig, [ProviderInfo])
        case error(WizardError)
    }

    public static let suggestedModels = ["deepseek-r1:7b", "llama3.2"]

    public private(set) var url: URL
    public private(set) var selectedModel: String?
    public private(set) var models: Loadable<[String]> = .idle
    public private(set) var download: Loadable<DownloadProgress> = .idle
    public private(set) var wizard: WizardState = .closed

    /// Called with the new URL and model whenever they change, to persist them.
    @ObservationIgnored public var onChange: (URL, String?) -> Void = { _, _ in }
    @ObservationIgnored private let makeAPI: (URL) -> OllamaAPI
    @ObservationIgnored private let autoConfig: () async -> LocalAiAutoConfig
    @ObservationIgnored private var modelsTask: Task<Void, Never>?
    @ObservationIgnored private var downloadTask: Task<Void, Never>?
    @ObservationIgnored private var urlEditTask: Task<Void, Never>?

    public init(
        url: URL,
        selectedModel: String?,
        makeAPI: @escaping (URL) -> OllamaAPI = { OllamaAPI(baseURL: $0) },
        autoConfig: @escaping () async -> LocalAiAutoConfig
    ) {
        self.url = url
        self.selectedModel = selectedModel
        self.makeAPI = makeAPI
        self.autoConfig = autoConfig
    }

    /// The server answers and a model it has is selected.
    public var isConfigured: Bool {
        guard let installed = models.value, let selectedModel, !selectedModel.isEmpty else { return false }
        return installed.contains(selectedModel)
    }

    public var isDownloading: Bool {
        if case .loading = download { return true }
        if case .loaded = download { return true }
        return false
    }

    // MARK: - Models

    public func retryModels() {
        modelsTask?.cancel()
        modelsTask = Task { await loadModels() }
    }

    public func loadModels() async {
        models = .loading
        do {
            let installed = try await makeAPI(url).models().map(\.model)
            guard !Task.isCancelled else { return }
            models = .loaded(installed)
            // Like the Compose app: a single model is picked without asking.
            if installed.count == 1, selectedModel != installed[0] {
                select(model: installed[0])
            }
        } catch is CancellationError {
            return
        } catch {
            models = .failed(Self.message(for: error))
        }
    }

    /// Typed in the URL field; saved and probed once the typing pauses.
    public func changeURL(_ text: String) {
        urlEditTask?.cancel()
        let trimmed = text.trimmingCharacters(in: .whitespaces)
        guard let url = URL(string: trimmed), url.scheme != nil, url.host() != nil else { return }
        urlEditTask = Task {
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled else { return }
            set(url: url)
            retryModels()
        }
    }

    public func select(model: String) {
        selectedModel = model
        onChange(url, selectedModel)
    }

    // MARK: - Downloads

    public func download(model: String, onComplete: @escaping () -> Void = {}) {
        let model = model.trimmingCharacters(in: .whitespaces)
        guard !model.isEmpty else { return }

        downloadTask?.cancel()
        download = .loading
        downloadTask = Task {
            do {
                for try await progress in makeAPI(url).pull(model: model) {
                    download = .loaded(DownloadProgress(
                        model: model,
                        status: progress.status ?? "",
                        completed: progress.completed,
                        total: progress.total
                    ))
                }
                guard !Task.isCancelled else { return }
                download = .idle
                await loadModels()
                if selectedModel == nil || selectedModel?.isEmpty == true {
                    select(model: model)
                }
                onComplete()
            } catch is CancellationError {
                download = .idle
            } catch {
                download = .failed(Self.message(for: error))
                if case .closed = wizard {} else {
                    wizard = .error(.downloadFailed(Self.message(for: error)))
                }
            }
        }
    }

    public func delete(model: String) {
        Task {
            do {
                try await makeAPI(url).delete(model: model)
                if selectedModel == model {
                    selectedModel = nil
                    onChange(url, nil)
                }
                await loadModels()
            } catch {
                models = .failed(Self.message(for: error))
            }
        }
    }

    /// What the server said, or that it isn't running: a local server that refuses the
    /// connection isn't "offline".
    static func message(for error: Error) -> String {
        if let error = error as? OllamaError { return error.message }
        if let urlError = error as? URLError, [.cannotConnectToHost, .cannotFindHost, .timedOut, .networkConnectionLost].contains(urlError.code) {
            return String(localized: "Local AI isn't running at this address.")
        }
        return error.userMessage
    }

    // MARK: - Wizard

    /// Detects Ollama and llmman and proposes the models of the backend, so the user only has
    /// to pick a size.
    public func openWizard() {
        wizard = .detecting
        Task {
            let config = await autoConfig()
            var providers: [ProviderInfo] = []
            for (name, address) in [("Ollama", config.ollamaUrl), ("llmman", config.llmmanUrl)] {
                guard let url = URL(string: address) else { continue }
                let isAvailable = (try? await makeAPI(url).models()) != nil
                providers.append(ProviderInfo(name: name, url: url, isAvailable: isAvailable))
            }
            guard case .detecting = wizard else { return }
            wizard = providers.contains(where: \.isAvailable) ? .selecting(config, providers) : .error(.noProviderDetected)
        }
    }

    /// Saves the provider and the model, starts downloading it and closes the wizard.
    public func confirmWizard(providerURL: URL, tier: LocalAiAutoConfig.ModelTier, onDownloadStarted: () -> Void = {}) {
        set(url: providerURL)
        selectedModel = tier.modelName
        onChange(url, selectedModel)
        wizard = .closed
        download(model: tier.modelName)
        onDownloadStarted()
    }

    public func closeWizard() {
        wizard = .closed
    }

    private func set(url: URL) {
        guard url != self.url else { return }
        self.url = url
        models = .idle
        onChange(url, selectedModel)
    }
}
