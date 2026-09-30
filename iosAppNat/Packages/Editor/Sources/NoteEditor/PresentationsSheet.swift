import SwiftUI
import WrData
import WrDesign
import WrModels

/// The presentations of the document, opened from the side menu: the ones generated before,
/// and a button that asks the AI for a new one. A presentation opens in its own window.
struct PresentationsSheet: View {
    let viewModel: PresentationsViewModel
    @Environment(\.dismiss) private var dismiss
    #if os(macOS)
    @Environment(\.openWindow) private var openWindow
    #endif

    var body: some View {
        NavigationStack {
            WrSheetList {
                Section("Presentations") {
                    if viewModel.presentations.isEmpty, !viewModel.isLoading {
                        Text("No presentations yet. Create one and the AI writes the slides from this document.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(viewModel.presentations) { presentation in
                        WrSheetRow(verbatim: presentation.title, systemImage: "play.rectangle") {
                            open(presentation)
                        } trailing: {
                            Text(subtitle(of: presentation))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                        .accessibilityIdentifier("presentations.item")
                        .contextMenu {
                            Button("Delete", systemImage: "trash", role: .destructive) {
                                Task { await viewModel.delete(presentation) }
                            }
                        }
                    }
                }

                Section {
                    if viewModel.isGenerating {
                        HStack(spacing: 12) {
                            ProgressView()
                                .controlSize(.small)
                            Text("The AI is writing the slides…")
                            Spacer()
                            Button("Cancel") { viewModel.cancel() }
                                .accessibilityIdentifier("presentations.cancel")
                        }
                    } else {
                        WrSheetRow("Create new presentation", systemImage: "sparkles", tint: WrColors.accent) {
                            generate()
                        }
                        .accessibilityIdentifier("presentations.create")
                    }
                }
            }
            .navigationTitle("Presentations")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task { await viewModel.load() }
            .alert(
                "Could not create the presentation",
                isPresented: Binding(get: { viewModel.error != nil }, set: { if !$0 { viewModel.error = nil } })
            ) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(viewModel.error ?? "")
            }
        }
        .presentationDetents([.medium, .large])
        .tint(WrColors.accent)
    }

    private func subtitle(of presentation: Presentation) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(presentation.createdAt) / 1000)
        let slides = String(localized: "\(presentation.slides.count) slides")
        return "\(slides) · \(date.formatted(date: .abbreviated, time: .shortened))"
    }

    /// The generation goes on if the sheet closes meanwhile: the view model outlives it.
    private func generate() {
        Task {
            if let presentation = await viewModel.generate() {
                open(presentation)
            }
        }
    }

    private func open(_ presentation: Presentation) {
        #if os(macOS)
        openWindow(value: PresentationWindowRef(presentationId: presentation.id))
        #endif
        dismiss()
    }
}
