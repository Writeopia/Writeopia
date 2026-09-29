import Foundation

extension NoteEditorView {
    /// Adds `https://` when the scheme is missing; nil for empty input.
    static func normalizedURL(_ input: String) -> String? {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return trimmed.contains("://") ? trimmed : "https://" + trimmed
    }
}
