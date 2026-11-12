import SwiftUI
import WrData
import WrDesign

/// The legal document a sheet shows. Both texts are served by the same endpoint.
struct LegalDocumentRoute: Identifiable, Equatable {
    static let terms = LegalDocumentRoute(type: "terms")!
    static let privacy = LegalDocumentRoute(type: "privacy")!

    let type: String
    var id: String { type }

    init?(type: String) {
        guard type == "terms" || type == "privacy" else { return nil }
        self.type = type
    }

    var title: LocalizedStringKey {
        type == "privacy" ? "Privacy Policy" : "Terms of Service"
    }
}

/// Reads the Terms of Service or the Privacy Policy from the API.
struct LegalDocumentView: View {
    let type: String
    let load: (String) async throws -> LegalDocument

    @Environment(\.dismiss) private var dismiss
    @State private var phase = Phase.loading

    private enum Phase {
        case loading
        case loaded(LegalDocument)
        case failed
    }

    var body: some View {
        NavigationStack {
            Group {
                switch phase {
                case .loading:
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                case .loaded(let document):
                    ScrollView {
                        content(document)
                            .padding(24)
                            .frame(maxWidth: 680, alignment: .leading)
                            .frame(maxWidth: .infinity)
                    }
                case .failed:
                    VStack(spacing: 12) {
                        Text("Couldn't load this document.")
                            .foregroundStyle(.secondary)
                        Button("Retry") { Task { await fetch() } }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .navigationTitle(LegalDocumentRoute(type: type)?.title ?? "Terms of Service")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        #if os(macOS)
        .frame(minWidth: 520, minHeight: 560)
        #endif
        .task { await fetch() }
    }

    private func fetch() async {
        phase = .loading

        do {
            phase = .loaded(try await load(type))
        } catch {
            phase = .failed
        }
    }

    @ViewBuilder
    private func content(_ document: LegalDocument) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Effective \(document.effectiveDate)")
                .font(.caption)
                .foregroundStyle(.secondary)

            ForEach(Array(document.sections.enumerated()), id: \.offset) { _, section in
                VStack(alignment: .leading, spacing: 6) {
                    Text(verbatim: section.heading)
                        .font(.headline)

                    ForEach(Array(Self.lines(of: section.body).enumerated()), id: \.offset) { _, line in
                        if line.hasPrefix("- ") {
                            Text(verbatim: "•  " + line.dropFirst(2))
                                .padding(.leading, 8)
                        } else {
                            Text(verbatim: line)
                        }
                    }
                }
            }
        }
    }

    /// Paragraphs are separated by a blank line and bullets start with "- ".
    static func lines(of body: String) -> [String] {
        body.components(separatedBy: "\n").filter { !$0.isEmpty }
    }
}
