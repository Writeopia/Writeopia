import Foundation
import WriteopiaUI

extension NoteEditorView {
    /// Adds `https://` when the scheme is missing; nil for empty input. See `LinkInput`.
    static func normalizedURL(_ input: String) -> String? {
        LinkInput.normalized(input)
    }
}
