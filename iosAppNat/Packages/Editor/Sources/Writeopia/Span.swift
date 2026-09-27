import WrModels

/// Inline formats that can be applied to selected text. Mirrors `Span` of the Kotlin SDK;
/// comments aren't supported here.
public enum Span: String, CaseIterable, Sendable {
    case bold = "BOLD"
    case italic = "ITALIC"
    case underline = "UNDERLINE"
    case highlightYellow = "HIGHLIGHT"
    case highlightGreen = "HIGHLIGHT_GREEN"
    case highlightRed = "HIGHLIGHT_RED"
    case link = "LINK"

    public static let highlights: [Span] = [.highlightYellow, .highlightGreen, .highlightRed]

    public var isHighlight: Bool { Self.highlights.contains(self) }
}
