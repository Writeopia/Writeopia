import Foundation

/// Turns what the user typed, or a `LINK` span's value, into something a browser can open.
public enum LinkInput {
    /// Trims the input and adds `https://` when it has no scheme; nil for empty input. An
    /// explicit scheme such as `mailto:` or `tel:` is kept, while a host with a port such as
    /// `localhost:8080` still gets `https://`.
    public static func normalized(_ input: String?) -> String? {
        guard let trimmed = input?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else { return nil }
        return hasScheme(trimmed) ? trimmed : "https://" + trimmed
    }

    /// A scheme is letters, digits, `+`, `-` or `.` up to the first colon (RFC 3986), as long as
    /// what follows is not a port number (`localhost:8080`, `writeopia.io:443/path`).
    private static func hasScheme(_ text: String) -> Bool {
        guard let colon = text.firstIndex(of: ":") else { return false }
        let scheme = text[..<colon]
        let rest = text[text.index(after: colon)...]
        guard let first = scheme.first, first.isLetter,
              scheme.allSatisfy({ $0.isLetter || $0.isNumber || "+-.".contains($0) }),
              !rest.isEmpty
        else { return false }
        return !isPort(rest)
    }

    private static func isPort(_ rest: Substring) -> Bool {
        let digits = rest.prefix(while: \.isNumber)
        guard !digits.isEmpty else { return false }
        let afterPort = rest.dropFirst(digits.count).first
        return afterPort == nil || "/?#".contains(afterPort!)
    }
}
