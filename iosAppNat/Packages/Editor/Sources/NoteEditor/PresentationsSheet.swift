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
    /// The presentation whose deletion waits for a confirmation.
    @State private var deleting: Presentation?

    var body: some View {
        NavigationStack {
            WrSheetList {
                Section("Presentations") {
                    if viewModel.presentations.isEmpty, !viewModel.isLoading {
                        Text("No presentations yet. Create one and the AI writes the slides from this document.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(viewModel.presentations) { presentation in
                        row(presentation)
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
            .confirmationDialog(
                "Delete this presentation?",
                isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
                titleVisibility: .visible,
                presenting: deleting
            ) { presentation in
                Button("Delete presentation", role: .destructive) {
                    Task { await viewModel.delete(presentation) }
                }
            } message: { presentation in
                Text("\"\(presentation.title)\" will be removed from this device. This can't be undone.")
            }
            .alert(
                viewModel.isGenerating || viewModel.lastActionWasGenerate ? "Could not create the presentation" : "Could not load the presentations",
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

    /// A presentation: its title and date open it, the trash at the end deletes it.
    private func row(_ presentation: Presentation) -> some View {
        HStack(spacing: 12) {
            Button {
                open(presentation)
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "play.rectangle")
                        .frame(width: 22)
                    Text(verbatim: presentation.title)
                    Spacer()
                    Text(subtitle(of: presentation))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .foregroundStyle(WrColors.textLight)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("presentations.item")

            Button {
                deleting = presentation
            } label: {
                Image(systemName: "trash")
                    .foregroundStyle(.red)
                    .frame(width: 22)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .help("Delete presentation")
            .accessibilityLabel("Delete presentation")
            .accessibilityIdentifier("presentations.delete")
        }
        .contextMenu {
            Button("Delete", systemImage: "trash", role: .destructive) {
                deleting = presentation
            }
        }
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
