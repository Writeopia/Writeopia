import SwiftUI
import WriteopiaUI
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif
import WrData
import WrDesign

/// Menu of the top right button, like `NoteGlobalActionsMenu` of the Compose app: lock the
/// document, pick the font, export it and publish it to the web.
struct NoteMenuSheet: View {
    @Bindable var viewModel: NoteEditorViewModel
    let onPublishClick: () -> Void
    let onDelete: () -> Void
    @State private var confirmsDelete = false
    @State private var exportedFile: ExportedFile?
    @State private var exportError: String?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            WrSheetList {
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
                        WrSheetRow(verbatim: format.title, systemImage: format.systemImage) {
                            export(format)
                        }
                        .accessibilityIdentifier("menu.export.\(format.rawValue)")
                    }
                }

                Section("Publish") {
                    WrSheetRow("Publish to Web", systemImage: viewModel.isPublished ? "globe.badge.chevron.backward" : "globe") {
                        dismiss()
                        onPublishClick()
                    }
                    .accessibilityIdentifier("menu.publish")
                }

                Section {
                    WrSheetRow("Delete document", systemImage: "trash", tint: .red) {
                        confirmsDelete = true
                    }
                    .accessibilityIdentifier("menu.delete")
                    .confirmationDialog(
                        "Delete this document?",
                        isPresented: $confirmsDelete,
                        titleVisibility: .visible
                    ) {
                        Button("Delete document", role: .destructive) {
                            dismiss()
                            onDelete()
                        }
                    } message: {
                        Text("It will be removed from every device. This can't be undone.")
                    }
                }
            }
            .navigationTitle("Document")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            #if os(iOS)
            .sheet(item: $exportedFile) { file in
                ShareSheet(items: [file.url])
                    .presentationDetents([.medium, .large])
            }
            #else
            .onChange(of: exportedFile) { _, file in
                guard let file else { return }
                ExportSaver.save(file.url)
                exportedFile = nil
            }
            #endif
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

struct ExportedFile: Identifiable, Equatable {
    let url: URL
    var id: URL { url }
}

struct FontOption: View {
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
                    .fill(isSelected ? WrColors.accent.opacity(0.12) : WrColors.tertiaryFill)
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
    @Environment(\.openURL) private var openURL

    var body: some View {
        NavigationStack {
            WrSheetList {
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
                        WrSheetRow(copied ? "Link copied!" : "Copy Link", systemImage: copied ? "checkmark" : "doc.on.doc") {
                            #if canImport(UIKit)
                            UIPasteboard.general.url = viewModel.siteURL
                            #else
                            NSPasteboard.general.clearContents()
                            NSPasteboard.general.setString(viewModel.siteURL.absoluteString, forType: .string)
                            #endif
                            copied = true
                        }
                        WrSheetRow("Open in Safari", systemImage: "safari") {
                            openURL(viewModel.siteURL)
                        }
                    }
                }

                Section {
                    WrSheetRow(
                        viewModel.isPublished ? "Unpublish" : "Publish",
                        systemImage: viewModel.isPublished ? "globe.badge.chevron.backward" : "globe",
                        tint: viewModel.isPublished ? .red : WrColors.accent
                    ) {
                        Task { await viewModel.setPublished(!viewModel.isPublished) }
                    } trailing: {
                        if viewModel.isPublishing {
                            ProgressView()
                                .controlSize(.small)
                        }
                    }
                    .disabled(viewModel.isPublishing)
                    .accessibilityIdentifier("publish.toggle")
                }
            }
            .navigationTitle("Publish to Web")
            .toolbarTitleDisplayMode(.inline)
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

#if os(iOS)
/// `UIActivityViewController` for sharing exported files.
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
#endif

extension ExportFormat {
    var title: String {
        switch self {
        case .json: String(localized: "Export as Json")
        case .markdown: String(localized: "Export as Markdown")
        }
    }

    var systemImage: String {
        switch self {
        case .json: "curlybraces"
        case .markdown: "text.document"
        }
    }
}
