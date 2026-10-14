import Foundation

/// Tells whether a text is a single web address, as pasted from a browser. Mirrors
/// `LinkDetector` of the Compose app.
public enum LinkDetector {
    private static let pattern = try! NSRegularExpression(pattern: "^https?://[^\\s]+$", options: [.caseInsensitive])

    /// True when `text`, trimmed, is one web address and nothing else.
    public static func isLink(_ text: String) -> Bool {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let range = NSRange(trimmed.startIndex..., in: trimmed)
        guard pattern.firstMatch(in: trimmed, range: range) != nil else { return false }

        // The address needs a host after the scheme.
        guard let schemeEnd = trimmed.range(of: "://") else { return false }
        let host = trimmed[schemeEnd.upperBound...]
            .prefix { $0 != "/" && $0 != "?" && $0 != "#" }
        return !host.isEmpty
    }

    /// True when an empty step received a whole link at once, which is what a paste does.
    /// Typing an address key by key never goes from empty to a full link in one input.
    public static func isPastedLink(previous: String?, new: String) -> Bool {
        (previous ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && isLink(new)
    }
}
