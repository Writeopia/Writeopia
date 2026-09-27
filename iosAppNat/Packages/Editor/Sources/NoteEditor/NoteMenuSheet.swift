#if canImport(UIKit)
import SwiftUI
import UIKit
import WriteopiaUI
import WrData
import WrDesign

/// Menu of the top right button, like `NoteGlobalActionsMenu` of the Compose app: lock the
/// document, pick the font, export it and publish it to the web.
struct NoteMenuSheet: View {
    @Bindable var viewModel: NoteEditorViewModel
    let onPublishClick: () -> Void
    @State private var exportedFile: ExportedFile?
    @State private var exportError: String?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Actions") {
                    Toggle(isOn: Binding(get: { viewModel.isLocked }, set: { _ in viewModel.toggleLock() })) {
                        Label("Lock document", systemImage: viewModel.isLocked ? "lock.fill" : "lock.open")
                    }
                    .tint(WrColors.accent)
                    .accessibilityIdentifier("menu.lock")
                }

                Section("Font") {
                    HStack(spacing: 8) {
                        ForEach(EditorFont.allCases) { font in
                            FontOption(font: font, isSelected: viewModel.fontFamily == font) {
                                viewModel.changeFontFamily(font)
                            }
                        }
                    }
                    .listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
                }

                Section("Export") {
                    ForEach(ExportFormat.allCases) { format in
                        Button {
                            export(format)
                        } label: {
                            Label(format.title, systemImage: format.systemImage)
                        }
                        .accessibilityIdentifier("menu.export.\(format.rawValue)")
                    }
                }

                Section("Publish") {
                    Button {
                        dismiss()
                        onPublishClick()
                    } label: {
                        Label {
                            Text("Publish to Web")
                        } icon: {
                            Image(systemName: viewModel.isPublished ? "globe.badge.chevron.backward" : "globe")
                        }
                    }
                    .accessibilityIdentifier("menu.publish")
                }
            }
            .navigationTitle("Document")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(item: $exportedFile) { file in
                ShareSheet(items: [file.url])
                    .presentationDetents([.medium, .large])
            }
            .alert(
                "Could not export",
                isPresented: Binding(get: { exportError != nil }, set: { if !$0 { exportError = nil } })
            ) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(exportError ?? "")
            }
        }
        .presentationDetents([.medium, .large])
        .tint(WrColors.accent)
    }

    private func export(_ format: ExportFormat) {
        do {
            exportedFile = ExportedFile(url: try viewModel.exportFile(format))
        } catch {
            exportError = error.localizedDescription
        }
    }
}

private struct ExportedFile: Identifiable {
    let url: URL
    var id: URL { url }
}

private struct FontOption: View {
    let font: EditorFont
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Text("Aa")
                    .font(font.font(size: 22, weight: .semibold))
                Text(font.title)
                    .font(.caption2)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .foregroundStyle(isSelected ? WrColors.accent : .primary)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(isSelected ? WrColors.accent.opacity(0.12) : Color(uiColor: .tertiarySystemFill))
            )
            .overlay {
                RoundedRectangle(cornerRadius: 10)
                    .strokeBorder(isSelected ? WrColors.accent : Color.clear, lineWidth: 1.5)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(font.title) font")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier("menu.font.\(font.rawValue)")
    }
}

/// Publish / unpublish, and the public link. Mirrors `PublishDialog` of the Compose app.
struct PublishSheet: View {
    @Bindable var viewModel: NoteEditorViewModel
    @State private var copied = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 12) {
                        Image(systemName: viewModel.isPublished ? "globe" : "globe.badge.chevron.backward")
                            .font(.title2)
                            .foregroundStyle(viewModel.isPublished ? WrColors.accent : .secondary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(viewModel.isPublished ? "Published" : "Not published")
                                .font(.headline)
                            Text(viewModel.isPublished
                                 ? "Anyone with the link can read this document."
                                 : "Publish to share a read-only version of this document on the web.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }

                if viewModel.isPublished {
                    Section("Link") {
                        Text(viewModel.siteURL.absoluteString)
                            .font(.callout.monospaced())
                            .textSelection(.enabled)
                        Button {
                            UIPasteboard.general.url = viewModel.siteURL
                            copied = true
                        } label: {
                            Label(copied ? "Link copied!" : "Copy Link", systemImage: copied ? "checkmark" : "doc.on.doc")
                        }
                        Link(destination: viewModel.siteURL) {
                            Label("Open in Safari", systemImage: "safari")
                        }
                    }
                }

                Section {
                    Button(role: viewModel.isPublished ? .destructive : nil) {
                        Task { await viewModel.setPublished(!viewModel.isPublished) }
                    } label: {
                        HStack {
                            Text(viewModel.isPublished ? "Unpublish" : "Publish")
                            if viewModel.isPublishing {
                                Spacer()
                                ProgressView()
                            }
                        }
                    }
                    .disabled(viewModel.isPublishing)
                    .accessibilityIdentifier("publish.toggle")
                }
            }
            .navigationTitle("Publish to Web")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task { await viewModel.loadPublishState() }
            .alert(
                "Something went wrong",
                isPresented: Binding(get: { viewModel.publishError != nil }, set: { if !$0 { viewModel.publishError = nil } })
            ) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(viewModel.publishError ?? "")
            }
        }
        .presentationDetents([.medium, .large])
        .tint(WrColors.accent)
    }
}

/// `UIActivityViewController` for sharing exported files.
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

extension ExportFormat {
    var title: String {
        switch self {
        case .json: "Export as Json"
        case .markdown: "Export as Markdown"
        }
    }

    var systemImage: String {
        switch self {
        case .json: "curlybraces"
        case .markdown: "text.document"
        }
    }
}
#endif
