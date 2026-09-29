import Observation
import SwiftUI
import WrData
import WrDesign
import WrModels
import WrNetwork
import WrSession

@Observable
final class AiSettingsViewModel {
    private(set) var usage: AiUsage?
    private(set) var isLoading = false
    private(set) var errorMessage: String?

    let session: AppSession

    init(session: AppSession) {
        self.session = session
    }

    func loadUsage() async {
        guard session.isOnline else { return }

        isLoading = true
        defer { isLoading = false }

        do {
            usage = try await session.aiAPI.usage()
            errorMessage = nil
        } catch {
            errorMessage = error.userMessage
        }
    }
}

struct AiSettingsView: View {
    @State private var viewModel: AiSettingsViewModel
    @State private var appleIntelligence = AppleIntelligenceAi.status
    @Environment(\.scenePhase) private var scenePhase

    init(session: AppSession) {
        _viewModel = State(initialValue: AiSettingsViewModel(session: session))
    }

    var body: some View {
        @Bindable var session = viewModel.session
        Form {
            appleIntelligenceSection

            if viewModel.session.isOnline {
                Section {
                    Picker("Run AI with", selection: $session.aiProvider) {
                        ForEach(AiProvider.allCases) { provider in
                            Text(provider.title).tag(provider)
                        }
                    }
                    .accessibilityIdentifier("settings.ai.provider")
                } header: {
                    Text("Provider")
                } footer: {
                    Text(providerFooter)
                }

                usageSection
            } else if !appleIntelligence.isAvailable {
                Section {
                    Text("Sign in to the open space to use the cloud AI on this device.")
                        .foregroundStyle(.secondary)
                }
            }
        }
        .task { await viewModel.loadUsage() }
        .refreshable { await viewModel.loadUsage() }
        // Apple Intelligence may have been turned on in the Settings app, or finished downloading.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { appleIntelligence = AppleIntelligenceAi.status }
        }
        .navigationTitle("AI")
    }

    private var appleIntelligenceSection: some View {
        Section {
            Label {
                VStack(alignment: .leading, spacing: 4) {
                    Text(appleIntelligence.isAvailable ? "Available" : "Unavailable")
                        .font(.headline)
                    Text(appleIntelligence.message)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } icon: {
                Image(systemName: appleIntelligence.isAvailable ? "apple.intelligence" : "exclamationmark.triangle")
                    .foregroundStyle(appleIntelligence.isAvailable ? WrColors.accent : .orange)
            }
            .padding(.vertical, 4)
            .accessibilityIdentifier("settings.ai.appleIntelligence")

            if appleIntelligence == .notEnabled, let url = URL(string: UIApplication.openSettingsURLString) {
                Link("Open Settings", destination: url)
            }
        } header: {
            Text("Apple Intelligence")
        } footer: {
            Text("Summaries, action points, FAQs, tags and prompts run on this device, even offline and in the private space.")
        }
    }

    private var providerFooter: String {
        switch viewModel.session.aiProvider {
        case .appleIntelligence where !appleIntelligence.isAvailable:
            String(localized: "Apple Intelligence isn't available, so the cloud AI is used for now.")
        case .appleIntelligence:
            String(localized: "AI runs on this device and doesn't use your monthly tokens.")
        case .cloud:
            String(localized: "AI runs on the Writeopia servers and uses your monthly tokens.")
        }
    }

    @ViewBuilder
    private var usageSection: some View {
        Section {
            if let usage = viewModel.usage {
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
            } else if viewModel.isLoading {
                HStack {
                    ProgressView()
                    Text("Loading usage…").foregroundStyle(.secondary)
                }
            } else if let error = viewModel.errorMessage {
                Text(error).foregroundStyle(.red)
            }
        } header: {
            Text("Cloud AI usage")
        }
    }
}

extension Int64 {
    var compactFormatted: String {
        formatted(.number.notation(.compactName).precision(.fractionLength(0...1)))
    }
}
