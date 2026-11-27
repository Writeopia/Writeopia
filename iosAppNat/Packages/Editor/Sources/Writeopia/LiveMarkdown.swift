import Foundation
import WrModels

/// Turns the Markdown that Claude Code sends into blocks of the editor, one step per block, in
/// order. Positions are left to the writer, which places the blocks (`Rank.none` here).
///
/// Mirrors `LiveMarkdown.kt` of the Compose app: both must read the same Markdown into the same
/// blocks and spans (offsets in UTF-16, like the spans of the editor).
///
/// Blocks: `#`..`######` headings (H1..H4, deeper ones are H4), `- [ ]`/`- [x]` checks,
/// `-`/`*`/`+` bullets, `>` quotes (highlighted blocks), fenced code (one code block per line),
/// `---` dividers, a line holding only `![alt](url)` (an image), `$$latex$$` (an equation, also
/// over several lines); anything else is a paragraph. Blank lines only separate blocks.
///
/// Inline: `**bold**`, `*italic*`/`_italic_`, `==highlight==`, `[text](url)`, bare `http(s)://`
/// addresses (links), `` `code` `` (kept as plain text) and `\` escapes.
public enum LiveMarkdown {
    private static let equationDelimiter = "$$"

    public static func blocks(_ markdown: String) -> [StoryStep] {
        let lines = markdown.replacingOccurrences(of: "\r\n", with: "\n")
            .split(separator: "\n", omittingEmptySubsequences: false)
            .map(String.init)
        var result: [StoryStep] = []
        var index = 0

        while index < lines.count {
            let trimmed = lines[index].trimmed

            if let fence = Fence(opening: trimmed) {
                index += 1
                while index < lines.count, !fence.closes(lines[index].trimmed) {
                    result.append(StoryStep(type: .codeBlock, text: lines[index].trimmedEnd, position: Rank.none))
                    index += 1
                }
                index += 1 // the closing fence
                continue
            }

            if trimmed.hasPrefix(equationDelimiter) {
                let delimiter = equationDelimiter
                if trimmed.count > delimiter.count * 2, trimmed.hasSuffix(delimiter) {
                    result.append(equation(String(trimmed.dropFirst(delimiter.count).dropLast(delimiter.count))))
                    index += 1
                    continue
                }

                if let closing = (index + 1..<lines.count).first(where: { lines[$0].trimmed.hasSuffix(delimiter) }) {
                    var parts = [String(trimmed.dropFirst(delimiter.count))]
                    parts += (index + 1..<closing).map { lines[$0].trimmed }
                    parts.append(String(lines[closing].trimmed.dropLast(delimiter.count)))
                    result.append(equation(parts.map(\.trimmed).joined(separator: " ")))
                    index = closing + 1
                    continue
                }
            }

            if let step = block(trimmed) { result.append(step) }
            index += 1
        }

        return result
    }

    private static func equation(_ latex: String) -> StoryStep {
        StoryStep(type: .equation, text: latex.trimmed, position: Rank.none)
    }

    private static func block(_ trimmed: String) -> StoryStep? {
        if trimmed.isEmpty { return nil }

        if let match = trimmed.wholeMatch(of: /(#{1,6})\s+(.*)/) {
            let tag: BlockTag = switch match.1.count {
            case 1: .h1
            case 2: .h2
            case 3: .h3
            default: .h4
            }
            return text(.text, String(match.2), tags: [TagInfo(tag: tag.rawValue)])
        }

        if trimmed.wholeMatch(of: /(-{3,}|\*{3,}|_{3,})/) != nil {
            return StoryStep(type: .divider, position: Rank.none)
        }

        if let match = trimmed.wholeMatch(of: /[-*+]\s+\[([ xX])\]\s+(.*)/) {
            var step = text(.checkItem, String(match.2))
            step.checked = match.1 != " "
            return step
        }

        if let match = trimmed.wholeMatch(of: /[-*+]\s+(.*)/) {
            return text(.unorderedListItem, String(match.1))
        }

        if let match = trimmed.wholeMatch(of: /!\[([^\]]*)\]\((\S+?)\)/) {
            let alt = String(match.1)
            return StoryStep(
                type: .image,
                text: alt.trimmingCharacters(in: .whitespaces).isEmpty ? nil : alt,
                url: String(match.2),
                position: Rank.none
            )
        }

        if let match = trimmed.wholeMatch(of: /> ?(.*)/) {
            return text(.text, String(match.1), tags: [TagInfo(tag: BlockTag.box.rawValue)])
        }

        return text(.text, trimmed)
    }

    private static func text(_ type: StoryType, _ markdown: String, tags: [TagInfo] = []) -> StoryStep {
        let (text, spans) = inline(markdown.trimmed)
        return StoryStep(type: type, text: text, tags: tags, spans: spans, position: Rank.none)
    }

    private struct Fence {
        let char: Character
        let length: Int

        init?(opening trimmed: String) {
            guard let first = trimmed.first, first == "`" || first == "~" else { return nil }
            let length = trimmed.prefix(while: { $0 == first }).count
            guard length >= 3 else { return nil }
            self.char = first
            self.length = length
        }

        func closes(_ trimmed: String) -> Bool {
            trimmed.count >= length && trimmed.allSatisfy { $0 == char }
        }
    }

    // MARK: - Inline

    /// The text without the inline markers, and the spans they meant. Offsets are UTF-16.
    public static func inline(_ markdown: String) -> (String, [SpanInfo]) {
        let units = Array(markdown.utf16)
        var out: [UInt16] = []
        var spans: [SpanInfo] = []
        var open: [String: Int] = [:]
        var i = 0

        func at(_ index: Int) -> UInt16? { index >= 0 && index < units.count ? units[index] : nil }

        func starts(_ marker: String, _ index: Int) -> Bool {
            let marks = Array(marker.utf16)
            guard index + marks.count <= units.count else { return false }
            return Array(units[index..<index + marks.count]) == marks
        }

        func indexOf(_ marker: String, from: Int) -> Int? {
            var index = max(from, 0)
            while index < units.count {
                if starts(marker, index) { return index }
                index += 1
            }
            return nil
        }

        func toggle(_ marker: String, _ span: Span) {
            if let start = open.removeValue(forKey: marker) {
                if out.count > start { spans.append(SpanInfo(start: start, end: out.count, span: span.rawValue)) }
            } else {
                open[marker] = out.count
            }
        }

        // A marker after an odd run of backslashes is escaped, like the loop below reads it.
        func isEscaped(_ index: Int) -> Bool {
            var backslashes = 0
            while index - backslashes - 1 >= 0, units[index - backslashes - 1] == unit("\\") { backslashes += 1 }
            return backslashes % 2 == 1
        }

        func closesLater(_ marker: String, from: Int) -> Bool {
            let mark = marker.utf16.first!
            var found = indexOf(marker, from: from)
            while let start = found {
                if isEscaped(start) {
                    found = indexOf(marker, from: start + 1)
                    continue
                }
                if marker.utf16.count > 1 { return true }

                // A single `*` closes unless it is a `**` (a run of exactly two); `***` holds one.
                var end = start
                while end < units.count, units[end] == mark { end += 1 }
                if end - start != 2 { return true }
                found = indexOf(marker, from: end)
            }
            return false
        }

        func append(_ string: String) { out.append(contentsOf: string.utf16) }

        let escapable = Array("\\*_=[]()`#>-+!~".utf16)
        let asterisk = unit("*"), underscore = unit("_")
        let backtick = unit("`"), bracket = unit("["), backslash = unit("\\")
        let paren = unit("(")

        while i < units.count {
            let c = units[i]
            let next = at(i + 1)
            let previous = at(i - 1)

            if c == backslash, let next, escapable.contains(next) {
                out.append(next)
                i += 2
            } else if starts("**", i) || starts("==", i) {
                let marker = starts("**", i) ? "**" : "=="
                let span: Span = marker == "**" ? .bold : .highlightYellow
                if open[marker] != nil || (at(i + 2).map { !isWhitespace($0) } == true && closesLater(marker, from: i + 2)) {
                    toggle(marker, span)
                } else {
                    append(marker)
                }
                i += 2
            } else if c == asterisk || c == underscore {
                let marker = c == asterisk ? "*" : "_"
                let opens = next.map { !isWhitespace($0) } == true &&
                    (c == asterisk || previous.map { !isLetterOrDigit($0) } ?? true) &&
                    closesLater(marker, from: i + 1)
                let closes = open[marker] != nil &&
                    (c == asterisk || next.map { !isLetterOrDigit($0) } ?? true)
                if closes || (open[marker] == nil && opens) {
                    toggle(marker, .italic)
                } else {
                    out.append(c)
                }
                i += 1
            } else if c == backtick {
                if let close = indexOf("`", from: i + 1) {
                    out.append(contentsOf: units[(i + 1)..<close])
                    i = close + 1
                } else {
                    out.append(c)
                    i += 1
                }
            } else if c == bracket, let link = linkAt(units, i) {
                let start = out.count
                out.append(contentsOf: link.label)
                if !link.label.isEmpty {
                    spans.append(SpanInfo(start: start, end: out.count, span: Span.link.rawValue, extra: link.url))
                }
                i = link.end
            } else if (starts("https://", i) || starts("http://", i)) &&
                (previous == nil || isWhitespace(previous!) || previous == paren) {
                var end = i
                while end < units.count, !isWhitespace(units[end]) { end += 1 }
                let trailing = Array(".,;:!?)".utf16)
                while end > i, trailing.contains(units[end - 1]) { end -= 1 }
                let url = String(decoding: units[i..<end], as: UTF16.self)
                let start = out.count
                out.append(contentsOf: units[i..<end])
                spans.append(SpanInfo(start: start, end: out.count, span: Span.link.rawValue, extra: url))
                i = end
            } else {
                out.append(c)
                i += 1
            }
        }

        return (String(decoding: out, as: UTF16.self), spans)
    }

    private struct Link {
        let label: [UInt16]
        let url: String
        let end: Int
    }

    private static func linkAt(_ units: [UInt16], _ start: Int) -> Link? {
        let closeBracket = unit("]"), openParen = unit("("), closeParen = unit(")")
        let newline = unit("\n")

        var closeLabel: Int?
        var index = start + 1
        while index + 1 < units.count {
            if units[index] == closeBracket, units[index + 1] == openParen { closeLabel = index; break }
            index += 1
        }
        guard let closeLabel else { return nil }
        if units[(start + 1)..<closeLabel].contains(newline) { return nil }

        guard let closeUrl = units[(closeLabel + 2)...].firstIndex(of: closeParen), closeUrl > closeLabel + 2 else {
            return nil
        }
        let urlUnits = units[(closeLabel + 2)..<closeUrl]
        if urlUnits.contains(where: isWhitespace) { return nil }

        return Link(
            label: Array(units[(start + 1)..<closeLabel]),
            url: String(decoding: urlUnits, as: UTF16.self),
            end: closeUrl + 1
        )
    }

    /// Like Kotlin's `Char.isWhitespace` on a UTF-16 unit: half of a surrogate pair is not.
    private static func isWhitespace(_ unit: UInt16) -> Bool {
        guard let scalar = Unicode.Scalar(unit) else { return false }
        return scalar.properties.isWhitespace
    }

    /// Like Kotlin's `Char.isLetterOrDigit` on a UTF-16 unit: half of a surrogate pair is not.
    private static func isLetterOrDigit(_ unit: UInt16) -> Bool {
        guard let scalar = Unicode.Scalar(unit) else { return false }
        return scalar.properties.isAlphabetic || scalar.properties.numericType == .decimal
    }
}

/// The UTF-16 unit of an ASCII character.
private func unit(_ scalar: Unicode.Scalar) -> UInt16 { UInt16(scalar.value) }

private extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }

    var trimmedEnd: String {
        var copy = self
        while let last = copy.last, last.isWhitespace { copy.removeLast() }
        return copy
    }
}
