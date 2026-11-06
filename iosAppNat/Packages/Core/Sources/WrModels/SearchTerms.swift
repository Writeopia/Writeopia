import Foundation

/// The words of a search, the way the full-text searches of the apps match them: runs of letters
/// and digits, lowercased, every one of them required and matched by prefix. Mirrors `FtsQuery`
/// (Compose apps) and `TsQuery` (backend), so a query finds the same documents everywhere.
public struct SearchTerms: Equatable, Sendable {
    /// The words searched, lowercased, in order.
    public let tokens: [String]

    public static let maxTokens = 8
    public static let maxTokenLength = 64

    public init(_ raw: String, maxTokens: Int = SearchTerms.maxTokens) {
        tokens = Array(Self.words(of: raw).map { String($0.prefix(Self.maxTokenLength)) }.prefix(maxTokens))
    }

    public var isEmpty: Bool { tokens.isEmpty }

    /// True when `text` holds every word, by prefix of a word and ignoring case: what the
    /// full-text searches find. Nothing matches an empty search.
    public func matches(_ text: String) -> Bool {
        guard !tokens.isEmpty else { return false }
        let words = Self.words(of: text)
        return tokens.allSatisfy { token in words.contains { $0.hasPrefix(token) } }
    }

    /// True when every word occurs somewhere in `text`, ignoring case, inside other words too:
    /// what the search inside a document highlights.
    public func occurs(in text: String) -> Bool {
        guard !tokens.isEmpty else { return false }
        return tokens.allSatisfy { token in text.range(of: token, options: .caseInsensitive) != nil }
    }

    /// Where the words occur in `text` (UTF-16 offsets, ignoring case, inside other words too),
    /// in order of position: the ranges to highlight.
    public func ranges(in text: String) -> [NSRange] {
        guard !tokens.isEmpty else { return [] }
        let whole = text as NSString
        var found: [NSRange] = []
        for token in tokens {
            var from = 0
            while from < whole.length {
                let range = whole.range(of: token, options: .caseInsensitive, range: NSRange(location: from, length: whole.length - from))
                guard range.location != NSNotFound else { break }
                found.append(range)
                from = range.location + 1
            }
        }
        return Array(Set(found.map { [$0.location, $0.length] }))
            .map { NSRange(location: $0[0], length: $0[1]) }
            .sorted { ($0.location, $0.length) < ($1.location, $1.length) }
    }

    /// `text` on one line, cut to about `maxLength` characters around the first word found,
    /// with an ellipsis on the sides that were cut. The whole text when it is short.
    public func snippet(of text: String, maxLength: Int = 90) -> String {
        let clean = text.replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespaces)
        let characters = Array(clean)
        guard characters.count > maxLength else { return clean }

        let hitOffset = ranges(in: clean).first.map { range in
            clean.distance(from: clean.startIndex, to: Range(range, in: clean)?.lowerBound ?? clean.startIndex)
        } ?? 0
        var start = max(0, hitOffset - maxLength / 3)
        let end = min(characters.count, start + maxLength)
        if end == characters.count { start = max(0, end - maxLength) }

        let prefix = start > 0 ? "…" : ""
        let suffix = end < characters.count ? "…" : ""
        return prefix + String(characters[start..<end]).trimmingCharacters(in: .whitespaces) + suffix
    }

    /// The runs of letters and digits of `text`, lowercased.
    static func words(of text: String) -> [String] {
        text.lowercased()
            .split(whereSeparator: { !($0.isLetter || $0.isNumber) })
            .map(String.init)
    }
}
