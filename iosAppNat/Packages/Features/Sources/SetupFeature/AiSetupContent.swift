import SwiftUI
import WrData
import WrDesign
import WrSession

/// The AI choices of the private space, on the v4 design: Apple Intelligence on the device,
/// or a local model served by Ollama or llmman. Each is a card with its status; the local AI
/// card opens "Configure local AI" and shows the download while it runs.
public struct AiSetupContent: View {
    @Environment(AppSession.self) private var session
    @Environment(\.scenePhase) private var scenePhase
    @State private var appleStatus = AppleIntelligenceAi.status
    @State private var showsManual = false

    public init() {}

    private var config: LocalAiConfigController { session.localAiConfig }

    public var body: some View {
        VStack(alignment: .leading, spacing: WrTheme.Space.s150) {
            appleIntelligenceCard
            localAiCard

            if session.aiProvider == .ollama {
                downloadProgress
                manualConfiguration
            }
        }
        .accessibilityIdentifier("setup.ai.provider")
        .onAppear {
            if case .idle = config.models { config.retryModels() }
        }
        .onChange(of: scenePhase) { _, phase in
            // Back from the system settings: the user may have turned Apple Intelligence on.
            if phase == .active { appleStatus = AppleIntelligenceAi.status }
        }
        .sheet(isPresented: Binding(get: { config.wizard != .closed }, set: { if !$0 { config.closeWizard() } })) {
            LocalAiWizardSheet(controller: config)
        }
        .animation(.easeOut(duration: 0.2), value: session.aiProvider)
    }

    // MARK: Cards

    // The Mac names the system and shows the computer, like the v4 design; iOS keeps the device.
    #if os(macOS)
    private static let appleDescription: LocalizedStringKey = "Built into macOS. No download."
    private static let appleSystemImage = "laptopcomputer"
    #else
    private static let appleDescription: LocalizedStringKey = "Built into this device. No download."
    private static let appleSystemImage = "apple.intelligence"
    #endif

    private var appleIntelligenceCard: some View {
        WrOptionCard(
            "Apple Intelligence",
            description: Self.appleDescription,
            systemImage: Self.appleSystemImage,
            isSelected: session.aiProvider == .appleIntelligence,
            status: appleStatus.isAvailable ? WrPill("Ready", kind: .success) : WrPill("Not ready yet", kind: .warning),
            meta: appleStatus.isAvailable ? nil : Text(verbatim: appleStatus.message),
            onSelect: { session.aiProvider = .appleIntelligence }
        ) {
            if appleStatus == .notEnabled, let url = SystemSettings.appleIntelligenceURL {
                Link("Open Settings", destination: url)
                    .buttonStyle(WrButtonStyle(kind: .ghost))
            } else if !appleStatus.isAvailable {
                WrButton("Try again", kind: .ghost) { appleStatus = AppleIntelligenceAi.status }
            }
        }
        .accessibilityIdentifier("setup.ai.appleIntelligence")
    }

    private var localAiCard: some View {
        WrOptionCard(
            "Local AI",
            description: "Runs with Ollama or llmman on this computer.",
            systemImage: "sparkles",
            iconStyle: .ai,
            isSelected: session.aiProvider == .ollama,
            status: localStatus,
            meta: localMeta,
            onSelect: { session.aiProvider = .ollama }
        ) {
            WrButton("Configure…", kind: .secondary, action: config.openWizard)
                .disabled(config.wizard != .closed)
                .accessibilityIdentifier("localAi.autoConfigure")
        }
        .accessibilityIdentifier("localAi.status")
    }

    private var localStatus: WrPill {
        if config.wizard == .detecting { return WrPill("Detecting", kind: .info) }
        if config.isDownloading { return WrPill("Downloading", kind: .info) }
        if config.isConfigured { return WrPill("Ready", kind: .success) }
        return WrPill("Not configured", kind: .neutral)
    }

    private var localMeta: Text? {
        if config.wizard == .detecting { return Text("Looking for Ollama or llmman…") }
        if config.isConfigured || config.isDownloading, let model = config.selectedModel {
            return Text(verbatim: model).monospaced()
        }
        return nil
    }

    // MARK: Download and manual setup

    @ViewBuilder
    private var downloadProgress: some View {
        switch config.download {
        case .idle:
            EmptyView()
        case .loading:
            WrStatusBanner("Preparing the download…")
        case .loaded(let progress):
            VStack(alignment: .leading, spacing: WrTheme.Space.s100) {
                HStack(spacing: WrTheme.Space.s100) {
                    WrSpinner(size: 14)
                    Text("Downloading \(progress.model)")
                        .wrTextStyle(.labelMedium)
                        .foregroundStyle(WrTheme.Colors.textPrimary)
                    Spacer(minLength: 0)
                    Text(verbatim: progressInfo(progress))
                        .wrTextStyle(.bodySmall)
                        .monospacedDigit()
                        .foregroundStyle(WrTheme.Colors.textTertiary)
                }
                WrProgressBar(value: progress.fraction)
                Text("You can go on. The download continues in the background.")
                    .wrTextStyle(.bodySmall)
                    .foregroundStyle(WrTheme.Colors.textTertiary)
            }
            .padding(WrTheme.Space.s200)
            .background(WrTheme.Colors.bgSubtle, in: RoundedRectangle(cornerRadius: WrTheme.Radius.lg))
            .accessibilityIdentifier("localAi.download.progress")
        case .failed(let message):
            Label {
                Text("Error when downloading model. \(message)")
            } icon: {
                Image(systemName: "exclamationmark.circle")
            }
            .wrTextStyle(.bodySmall)
            .foregroundStyle(WrTheme.Colors.danger)
        }
    }

    private var manualConfiguration: some View {
        DisclosureGroup(isExpanded: $showsManual) {
            LocalAiConfigView(showsSummary: false, presentsWizard: false)
                .padding(.top, WrTheme.Space.s150)
        } label: {
            Button {
                withAnimation(.snappy) { showsManual.toggle() }
            } label: {
                Text("Manual configuration")
                    .wrTextStyle(.labelMedium)
                    .foregroundStyle(WrTheme.Colors.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("localAi.manual")
        }
        .tint(WrTheme.Colors.textSecondary)
        .padding(.top, WrTheme.Space.s50)
    }

    private func progressInfo(_ progress: LocalAiConfigController.DownloadProgress) -> String {
        guard let completed = progress.completed, let total = progress.total else { return progress.status }
        let style = ByteCountFormatStyle(style: .file)
        return "\(completed.formatted(style)) / \(total.formatted(style))"
    }
}

public extension AiProvider {
    /// The providers that work without the backend.
    static var offlineChoices: [AiProvider] { [.appleIntelligence, .ollama] }
}
