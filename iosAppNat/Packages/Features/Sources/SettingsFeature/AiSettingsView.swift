import Observation
import SwiftUI
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

    init(session: AppSession) {
        _viewModel = State(initialValue: AiSettingsViewModel(session: session))
    }

    var body: some View {
        Group {
            if viewModel.session.isOnline {
                Form {
                    usageSection
                }
                .task { await viewModel.loadUsage() }
                .refreshable { await viewModel.loadUsage() }
            } else {
                OfflineNotice(
                    title: "AI needs an account",
                    message: "Sign in to the open space to use AI in your documents."
                )
            }
        }
        .navigationTitle("AI")
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
            Text("Cloud AI")
        }
    }
}

extension Int64 {
    var compactFormatted: String {
        formatted(.number.notation(.compactName).precision(.fractionLength(0...1)))
    }
}
