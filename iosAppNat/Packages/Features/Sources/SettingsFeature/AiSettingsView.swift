import Observation
import SwiftUI
import WrDesign
import WrModels
import WrNetwork
import WrSession
import WrStorage

@Observable
final class AiSettingsViewModel {
    static let defaultOllamaUrl = "http://localhost:11434"

    private(set) var usage: AiUsage?
    private(set) var isLoadingUsage = false
    private(set) var usageError: String?

    var localUrl: String
    private(set) var models: [String] = []
    private(set) var isLoadingModels = false
    private(set) var modelsError: String?
    var selectedModel: String {
        didSet { preferences.set(selectedModel.isEmpty ? nil : selectedModel, for: .localAiModel) }
    }

    let session: AppSession
    private var preferences: Preferences { session.preferences }

    init(session: AppSession) {
        self.session = session
        localUrl = session.preferences.string(.localAiUrl) ?? Self.defaultOllamaUrl
        selectedModel = session.preferences.string(.localAiModel) ?? ""
    }

    func loadUsage() async {
        guard session.isOnline else { return }

        isLoadingUsage = true
        defer { isLoadingUsage = false }

        do {
            usage = try await session.aiAPI.usage()
            usageError = nil
        } catch {
            usageError = error.userMessage
        }
    }

    /// Saves the URL and lists the models installed on that server.
    func connect() async {
        let url = localUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        preferences.set(url.isEmpty ? nil : url, for: .localAiUrl)
        guard !url.isEmpty else { return }

        isLoadingModels = true
        defer { isLoadingModels = false }

        do {
            models = try await session.ollamaAPI.models(baseURL: url)
            modelsError = models.isEmpty ? "No models installed. Pull one with `ollama pull <model>`." : nil
            if models.count == 1 || (!models.isEmpty && !models.contains(selectedModel)) {
                selectedModel = models[0]
            }
        } catch {
            models = []
            modelsError = "Couldn't reach a local AI server at \(url)."
        }
    }

    /// Uses the recommended defaults published by the backend.
    func useRecommendedUrl() async {
        if let config = try? await session.aiAPI.localAutoConfig() {
            localUrl = config.ollamaUrl
        } else {
            localUrl = Self.defaultOllamaUrl
        }
        await connect()
    }
}

struct AiSettingsView: View {
    @State private var viewModel: AiSettingsViewModel

    init(session: AppSession) {
        _viewModel = State(initialValue: AiSettingsViewModel(session: session))
    }

    var body: some View {
        Form {
            cloudSection
            localSection
        }
        .navigationTitle("AI")
        .task {
            await viewModel.loadUsage()
            await viewModel.connect()
        }
        .refreshable { await viewModel.loadUsage() }
    }

    @ViewBuilder
    private var cloudSection: some View {
        Section {
            if !viewModel.session.isOnline {
                Text("Cloud AI is available in the open space. Sign in to use frontier models.")
                    .foregroundStyle(.secondary)
            } else if let usage = viewModel.usage {
                VStack(alignment: .leading, spacing: 8) {
                    Text("\(usage.totalTokens.compactFormatted) / \(usage.quota.compactFormatted)")
                        .font(.title2.bold())
                        .monospacedDigit()
                    Text("Tokens used this month")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    ProgressView(value: usage.progress)
                        .tint(usage.progress > 0.9 ? .red : WrColors.accent)
                    Text(usage.progress, format: .percent.precision(.fractionLength(0)))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)

                LabeledContent("Requests", value: "\(usage.requestCount)")
                LabeledContent("Input tokens", value: usage.totalInputTokens.compactFormatted)
                LabeledContent("Output tokens", value: usage.totalOutputTokens.compactFormatted)
            } else if viewModel.isLoadingUsage {
                HStack {
                    ProgressView()
                    Text("Loading usage…").foregroundStyle(.secondary)
                }
            } else if let error = viewModel.usageError {
                Text(error).foregroundStyle(.red)
            }
        } header: {
            Text("Cloud AI")
        }
    }

    @ViewBuilder
    private var localSection: some View {
        Section {
            TextField("Server URL", text: $viewModel.localUrl)
                .keyboardType(.URL)
                .textContentType(.URL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .onSubmit { Task { await viewModel.connect() } }

            HStack {
                Button("Connect") {
                    Task { await viewModel.connect() }
                }
                Spacer()
                Button("Use recommended") {
                    Task { await viewModel.useRecommendedUrl() }
                }
                .font(.footnote)
            }
            .buttonStyle(.borderless)

            if viewModel.isLoadingModels {
                HStack {
                    ProgressView()
                    Text("Looking for models…").foregroundStyle(.secondary)
                }
            } else if !viewModel.models.isEmpty {
                Picker("Model", selection: $viewModel.selectedModel) {
                    ForEach(viewModel.models, id: \.self) { model in
                        Text(model).tag(model)
                    }
                }
            } else if let error = viewModel.modelsError {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(.orange)
            }
        } header: {
            Text("Local AI")
        } footer: {
            Text("Point Writeopia to an Ollama server, for example one running on your Mac in the same network. Requests never leave that machine.")
        }
    }
}

extension Int64 {
    var compactFormatted: String {
        formatted(.number.notation(.compactName).precision(.fractionLength(0...1)))
    }
}
