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
    private let showsSummary: Bool
    private let presentsWizard: Bool

    /// - Parameters:
    ///   - showsSummary: the status row and "Auto configure". Off when the screen shows them
    ///     itself, like the v4 setup, which keeps only the manual configuration here.
    ///   - presentsWizard: presents the "Auto configure" sheet. Off when the screen presents it.
    public init(showsSummary: Bool = true, presentsWizard: Bool = true) {
        self.showsSummary = showsSummary
        self.presentsWizard = presentsWizard
    }

    private var config: LocalAiConfigController { session.localAiConfig }

    public var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if showsSummary {
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
            }

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
        .sheet(isPresented: Binding(get: { presentsWizard && config.wizard != .closed }, set: { if !$0 { config.closeWizard() } })) {
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

/// "Auto configure", on the v4 design: detects the local AI and proposes a model per size,
/// like `LocalAiWizardDialog` of the Compose app.
struct LocalAiWizardSheet: View {
    let controller: LocalAiConfigController
    @State private var providerURL: URL?
    @State private var tier: LocalAiAutoConfig.ModelTier?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            switch controller.wizard {
            case .closed:
                EmptyView()
            case .detecting:
                WrDialogHeader("Configure local AI", description: "Looking for a local AI on this computer.", close: controller.closeWizard)
                WrStatusBanner("Looking for Ollama or llmman…")
                    .padding(.top, WrTheme.Space.s300)
                    .accessibilityIdentifier("localAi.wizard.detecting")
            case .selecting(let config, let providers):
                selecting(config, providers)
            case .error(let error):
                WrDialogHeader("Configuration failed", close: controller.closeWizard)
                Text(message(for: error))
                    .wrTextStyle(.bodyMedium)
                    .foregroundStyle(WrTheme.Colors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, WrTheme.Space.s150)
                HStack(spacing: WrTheme.Space.s100) {
                    Spacer()
                    WrButton("Close", kind: .ghost, action: controller.closeWizard)
                    WrButton("Try again", kind: .primary, action: controller.openWizard)
                        .keyboardShortcut(.defaultAction)
                }
                .padding(.top, WrTheme.Space.s300)
            }
        }
        .padding(WrTheme.Space.s300)
        .frame(width: WrTheme.Size.dialogMd, alignment: .topLeading)
        .background(WrTheme.Colors.bgRaised)
        .wrV4()
    }

    private func selecting(_ config: LocalAiAutoConfig, _ providers: [LocalAiConfigController.ProviderInfo]) -> some View {
        let chosenURL = providerURL ?? providers.first(where: \.isAvailable)?.url
        let chosenTier = tier ?? config.defaultTier

        return VStack(alignment: .leading, spacing: 0) {
            WrDialogHeader(
                "Configure local AI",
                description: "Writeopia downloads the model through your provider. After that it works offline.",
                close: controller.closeWizard
            )

            sectionTitle("Provider")
            VStack(spacing: 0) {
                ForEach(Array(providers.enumerated()), id: \.element.id) { index, provider in
                    if index > 0 { WrDivider() }
                    WrProviderRow(
                        provider.name,
                        isSelected: chosenURL == provider.url,
                        isAvailable: provider.isAvailable,
                        status: provider.isAvailable
                            ? WrPill("Detected", kind: .success)
                            : WrPill("Not installed", kind: .neutral),
                        action: { providerURL = provider.url }
                    ) {
                        if !provider.isAvailable, provider.name == "Ollama" {
                            Link("How to install", destination: URL(string: "https://ollama.com")!)
                                .buttonStyle(WrButtonStyle(kind: .ghost))
                        }
                    }
                }
            }
            .overlay {
                RoundedRectangle(cornerRadius: WrTheme.Radius.lg)
                    .strokeBorder(WrTheme.Colors.borderDefault)
            }

            sectionTitle("Model size")
            HStack(alignment: .top, spacing: WrTheme.Space.s100) {
                ForEach(config.modelTiers) { option in
                    WrTierCard(
                        title(for: option.type),
                        description: description(for: option.type),
                        model: option.modelName,
                        isSelected: chosenTier == option,
                        isRecommended: config.defaultTier == option,
                        action: { tier = option }
                    )
                    .accessibilityIdentifier("localAi.wizard.tier.\(option.type.rawValue.lowercased())")
                }
            }
            .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: WrTheme.Space.s100) {
                Spacer()
                WrButton("Cancel", kind: .ghost, action: controller.closeWizard)
                WrButton("Download & configure", kind: .primary) {
                    guard let url = chosenURL, let tier = chosenTier else { return }
                    controller.confirmWizard(providerURL: url, tier: tier)
                }
                .keyboardShortcut(.defaultAction)
                .disabled(chosenURL == nil || chosenTier == nil)
                .accessibilityIdentifier("localAi.wizard.confirm")
            }
            .padding(.top, WrTheme.Space.s300)
        }
    }

    private func sectionTitle(_ title: LocalizedStringKey) -> some View {
        Text(title)
            .textCase(.uppercase)
            .wrTextStyle(.overline)
            .foregroundStyle(WrTheme.Colors.textTertiary)
            // Figma: 20 pt above each section of the dialog.
            .padding(.top, WrTheme.Space.s200 + WrTheme.Space.s50)
            .padding(.bottom, WrTheme.Space.s100)
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
        case .light: "Fast. Good for short edits."
        case .medium: "Balanced for most writing."
        case .heavy: "Best quality, slower."
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
