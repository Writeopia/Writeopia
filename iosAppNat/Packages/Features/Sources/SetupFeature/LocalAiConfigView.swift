import SwiftUI
import WrData
import WrDesign
import WrSession

/// Configuration of Ollama or llmman, like `LocalAiConfigScreen` of the Compose app: status,
/// "Auto configure", and the manual URL, models and downloads.
public struct LocalAiConfigView: View {
    @Environment(AppSession.self) private var session
    @State private var urlText = ""
    @State private var modelToDownload = ""
    @State private var showsManual = false

    public init() {}

    private var config: LocalAiConfigController { session.localAiConfig }

    public var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            statusRow

            Button {
                config.openWizard()
            } label: {
                Label("Auto configure", systemImage: "wand.and.stars")
            }
            .buttonStyle(.borderedProminent)
            .tint(WrColors.accent)
            .disabled(config.wizard != .closed)
            .accessibilityIdentifier("localAi.autoConfigure")

            DisclosureGroup(isExpanded: $showsManual) {
                VStack(alignment: .leading, spacing: 16) {
                    urlField
                    modelsSection
                    downloadSection
                }
                .padding(.top, 12)
            } label: {
                // The title toggles the group too, not only the chevron.
                Button {
                    withAnimation(.snappy) { showsManual.toggle() }
                } label: {
                    Text("Manual Configuration")
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("localAi.manual")
            }
            .foregroundStyle(WrColors.textLight)
        }
        .onAppear {
            urlText = config.url.absoluteString
            if case .idle = config.models { config.retryModels() }
        }
        .onChange(of: config.url) { _, url in
            if URL(string: urlText) != url { urlText = url.absoluteString }
        }
        .sheet(isPresented: Binding(get: { config.wizard != .closed }, set: { if !$0 { config.closeWizard() } })) {
            LocalAiWizardSheet(controller: config)
        }
    }

    private var statusRow: some View {
        HStack(spacing: 10) {
            Image(systemName: config.isConfigured ? "checkmark.circle.fill" : "xmark.circle")
                .foregroundStyle(config.isConfigured ? .green : WrColors.textLighter)
            Text(config.isConfigured ? "AI configured" : "AI not configured")
                .font(.headline)
                .foregroundStyle(WrColors.textLight)
            if let model = config.selectedModel, config.isConfigured {
                Text(model)
                    .font(.caption.monospaced())
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(WrColors.divider.opacity(0.5), in: Capsule())
                    .foregroundStyle(WrColors.textLighter)
            }
        }
        .accessibilityIdentifier("localAi.status")
    }

    private var urlField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Url")
                .font(.subheadline.weight(.semibold))
            WrTextField("http://localhost:11434", text: $urlText, systemImage: "network")
                .onChange(of: urlText) { _, text in config.changeURL(text) }
                .accessibilityIdentifier("localAi.url")
        }
    }

    @ViewBuilder
    private var modelsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Available Models")
                .font(.subheadline.weight(.semibold))

            switch config.models {
            case .idle:
                EmptyView()
            case .loading:
                ProgressView()
                    .controlSize(.small)
            case .loaded(let models):
                if models.isEmpty {
                    Text("No models found")
                        .foregroundStyle(WrColors.textLighter)
                } else {
                    ForEach(models, id: \.self) { model in
                        modelRow(model)
                    }
                }
            case .failed(let message):
                VStack(alignment: .leading, spacing: 8) {
                    Text("Error when requesting models. Did you start Local AI?")
                        .foregroundStyle(.red)
                    Text(message)
                        .font(.footnote)
                        .foregroundStyle(WrColors.textLighter)
                    HStack {
                        Link("Get Ollama", destination: URL(string: "https://ollama.com")!)
                        Button("Retry", action: config.retryModels)
                    }
                }
            }
        }
    }

    private func modelRow(_ model: String) -> some View {
        let isSelected = config.selectedModel == model
        return HStack {
            Button {
                config.select(model: model)
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                        .foregroundStyle(isSelected ? WrColors.accent : WrColors.textLighter)
                    Text(model)
                        .foregroundStyle(WrColors.textLight)
                    Spacer()
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Button(role: .destructive) {
                config.delete(model: model)
            } label: {
                Image(systemName: "trash")
            }
            .buttonStyle(.borderless)
            .help("Delete model")
        }
        .padding(.vertical, 6)
        .padding(.horizontal, 10)
        .background(isSelected ? WrColors.accent.opacity(0.1) : .clear, in: RoundedRectangle(cornerRadius: 8))
        .accessibilityIdentifier("localAi.model.\(model)")
    }

    @ViewBuilder
    private var downloadSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Download Model")
                .font(.subheadline.weight(.semibold))

            HStack(spacing: 8) {
                Text("Suggestions:")
                    .font(.footnote)
                    .foregroundStyle(WrColors.textLighter)
                ForEach(LocalAiConfigController.suggestedModels, id: \.self) { model in
                    Button(model) { modelToDownload = model }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                }
            }

            HStack(spacing: 8) {
                WrTextField("Write your AI model", text: $modelToDownload, systemImage: "shippingbox")
                    .onSubmit(startDownload)
                Button(action: startDownload) {
                    Image(systemName: "arrow.down.circle")
                }
                .buttonStyle(.borderless)
                .disabled(modelToDownload.trimmingCharacters(in: .whitespaces).isEmpty || config.isDownloading)
                .help("Download model")
                .accessibilityIdentifier("localAi.download")
            }

            switch config.download {
            case .idle:
                EmptyView()
            case .loading:
                ProgressView()
                    .controlSize(.small)
            case .loaded(let progress):
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(progress.model)
                            .font(.footnote.weight(.semibold))
                        Spacer()
                        Text(progressInfo(progress))
                            .font(.footnote.monospacedDigit())
                            .foregroundStyle(WrColors.textLighter)
                    }
                    ProgressView(value: progress.fraction ?? 0)
                        .tint(WrColors.accent)
                }
            case .failed(let message):
                Text("Error when downloading model. \(message)")
                    .font(.footnote)
                    .foregroundStyle(.red)
            }
        }
    }

    private func progressInfo(_ progress: LocalAiConfigController.DownloadProgress) -> String {
        guard let completed = progress.completed, let total = progress.total else { return progress.status }
        let style = ByteCountFormatStyle(style: .file)
        return "\(completed.formatted(style)) / \(total.formatted(style))"
    }

    private func startDownload() {
        config.download(model: modelToDownload)
        modelToDownload = ""
    }
}

/// "Auto configure": detects the local AI and proposes a model per tier, like
/// `LocalAiWizardDialog` of the Compose app.
struct LocalAiWizardSheet: View {
    let controller: LocalAiConfigController
    @State private var providerURL: URL?
    @State private var tier: LocalAiAutoConfig.ModelTier?

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            switch controller.wizard {
            case .closed:
                EmptyView()
            case .detecting:
                HStack(spacing: 12) {
                    ProgressView()
                    Text("Detecting Local AI")
                        .font(.headline)
                }
                .frame(maxWidth: .infinity, minHeight: 120)
            case .selecting(let config, let providers):
                selecting(config, providers)
            case .error(let error):
                VStack(alignment: .leading, spacing: 12) {
                    Text("Configuration Failed")
                        .font(.title2.bold())
                    Text(message(for: error))
                        .foregroundStyle(WrColors.textLighter)
                    HStack {
                        Spacer()
                        Button("Close", action: controller.closeWizard)
                        Button("Retry", action: controller.openWizard)
                            .buttonStyle(.borderedProminent)
                    }
                }
            }
        }
        .padding(24)
        .frame(minWidth: 420, idealWidth: 460)
        .background(WrColors.background)
    }

    private func selecting(_ config: LocalAiAutoConfig, _ providers: [LocalAiConfigController.ProviderInfo]) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Configure Local AI")
                .font(.title2.bold())

            Text("Select Provider")
                .font(.subheadline.weight(.semibold))
            ForEach(providers) { provider in
                let selected = (providerURL ?? providers.first(where: \.isAvailable)?.url) == provider.url
                Button {
                    providerURL = provider.url
                } label: {
                    HStack {
                        Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                            .foregroundStyle(selected ? WrColors.accent : WrColors.textLighter)
                        Text(provider.name)
                        Spacer()
                        Text(provider.isAvailable ? "Available" : "Not Detected")
                            .font(.caption)
                            .foregroundStyle(provider.isAvailable ? .green : WrColors.textLighter)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(!provider.isAvailable)
                .opacity(provider.isAvailable ? 1 : 0.5)
            }

            Text("Select Model Tier")
                .font(.subheadline.weight(.semibold))
            ForEach(config.modelTiers) { option in
                let selected = (tier ?? config.defaultTier) == option
                Button {
                    tier = option
                } label: {
                    HStack(alignment: .top) {
                        Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                            .foregroundStyle(selected ? WrColors.accent : WrColors.textLighter)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(title(for: option.type))
                                .font(.body.weight(.semibold))
                            Text(description(for: option.type))
                                .font(.caption)
                                .foregroundStyle(WrColors.textLighter)
                            Text(option.modelName)
                                .font(.caption.monospaced())
                                .foregroundStyle(WrColors.textLighter)
                        }
                        Spacer()
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }

            HStack {
                Spacer()
                Button("Cancel", action: controller.closeWizard)
                    .keyboardShortcut(.cancelAction)
                Button("Download & Configure") {
                    guard let url = providerURL ?? providers.first(where: \.isAvailable)?.url,
                          let tier = tier ?? config.defaultTier
                    else { return }
                    controller.confirmWizard(providerURL: url, tier: tier)
                }
                .buttonStyle(.borderedProminent)
                .tint(WrColors.accent)
                .keyboardShortcut(.defaultAction)
                .accessibilityIdentifier("localAi.wizard.confirm")
            }
        }
    }

    private func title(for tier: LocalAiAutoConfig.Tier) -> LocalizedStringKey {
        switch tier {
        case .light: "Light"
        case .medium: "Medium"
        case .heavy: "Heavy"
        }
    }

    private func description(for tier: LocalAiAutoConfig.Tier) -> LocalizedStringKey {
        switch tier {
        case .light: "Fast and efficient for basic tasks"
        case .medium: "Balanced performance for most use cases"
        case .heavy: "Maximum quality for complex tasks"
        }
    }

    private func message(for error: LocalAiConfigController.WizardError) -> String {
        switch error {
        case .noProviderDetected:
            String(localized: "Local AI was not found running on this machine. Please, install and start Ollama or llmman and try again.")
        case .downloadFailed(let message):
            String(localized: "Failed to download model. \(message)")
        }
    }
}
