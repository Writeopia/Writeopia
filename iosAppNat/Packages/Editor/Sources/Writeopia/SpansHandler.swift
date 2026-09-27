import WrModels

/// Keeps the inline spans (bold, italic, links...) attached to the right characters while the
/// text is edited. Spans can't be created in this editor, only preserved. Indices are UTF-16
/// offsets, like Kotlin strings.
public enum SpansHandler {
    /// Updates `spans` after `oldText` became `newText`.
    ///
    /// Text typed strictly inside a span extends it; text typed at its edges doesn't. Deleted
    /// characters shrink the spans that covered them, and empty spans are dropped.
    public static func adjust(_ spans: [SpanInfo], from oldText: String, to newText: String) -> [SpanInfo] {
        guard !spans.isEmpty, oldText != newText else { return spans }

        let old = Array(oldText.utf16)
        let new = Array(newText.utf16)

        var prefix = 0
        while prefix < old.count, prefix < new.count, old[prefix] == new[prefix] {
            prefix += 1
        }

        var suffix = 0
        while suffix < old.count - prefix, suffix < new.count - prefix,
              old[old.count - 1 - suffix] == new[new.count - 1 - suffix] {
            suffix += 1
        }

        let oldEditEnd = old.count - suffix
        let newEditEnd = new.count - suffix
        let delta = new.count - old.count

        func mapStart(_ index: Int) -> Int {
            if index <= prefix { return index }
            if index >= oldEditEnd { return index + delta }
            return prefix
        }

        func mapEnd(_ index: Int) -> Int {
            if index <= prefix { return index }
            if index >= oldEditEnd { return index + delta }
            return newEditEnd
        }

        return spans.compactMap { span in
            let start = mapStart(span.start)
            let end = mapEnd(span.end)
            guard start < end else { return nil }
            return SpanInfo(start: start, end: end, span: span.span, extra: span.extra)
        }
    }

    /// Splits `text` at every line break, returning each line with the spans that fall inside it.
    public static func splitLines(_ text: String, spans: [SpanInfo]) -> [(text: String, spans: [SpanInfo])] {
        let lines = text.components(separatedBy: "\n")
        var result: [(String, [SpanInfo])] = []
        var offset = 0

        for line in lines {
            let length = line.utf16.count
            let lineSpans = spans.compactMap { span -> SpanInfo? in
                let start = max(span.start, offset) - offset
                let end = min(span.end, offset + length) - offset
                guard start < end else { return nil }
                return SpanInfo(start: start, end: end, span: span.span, extra: span.extra)
            }
            result.append((line, lineSpans))
            offset += length + 1
        }

        return result
    }

    /// Spans of `spans` moved `offset` characters to the right.
    public static func shift(_ spans: [SpanInfo], by offset: Int) -> [SpanInfo] {
        spans.map { SpanInfo(start: $0.start + offset, end: $0.end + offset, span: $0.span, extra: $0.extra) }
    }

    /// True when every character of `start..<end` is covered by a span of kind `span`.
    public static func isFullyCovered(_ spans: [SpanInfo], span: String, start: Int, end: Int) -> Bool {
        guard start < end else { return false }
        var covered = start
        for info in spans.filter({ $0.span == span }).sorted(by: { $0.start < $1.start }) {
            if info.start > covered { break }
            covered = max(covered, info.end)
            if covered >= end { return true }
        }
        return false
    }

    /// Toggles `span` on `start..<end`, like the formatting buttons of the Compose editor: when
    /// the whole range already has it, it's removed from the range; otherwise the range gets it,
    /// merged with the spans of the same kind it touches.
    public static func toggle(_ span: String, start: Int, end: Int, in spans: [SpanInfo]) -> [SpanInfo] {
        guard start < end else { return spans }

        let others = spans.filter { $0.span != span }
        let same = spans.filter { $0.span == span }

        if isFullyCovered(spans, span: span, start: start, end: end) {
            // Cut the range out of every span of this kind.
            let remaining = same.flatMap { info -> [SpanInfo] in
                var pieces: [SpanInfo] = []
                if info.start < start {
                    pieces.append(SpanInfo(start: info.start, end: min(info.end, start), span: span, extra: info.extra))
                }
                if info.end > end {
                    pieces.append(SpanInfo(start: max(info.start, end), end: info.end, span: span, extra: info.extra))
                }
                return pieces
            }
            return others + remaining
        }

        // Merge the range with every span of this kind that overlaps or touches it.
        var mergedStart = start
        var mergedEnd = end
        var untouched: [SpanInfo] = []
        for info in same {
            if info.end >= start && info.start <= end {
                mergedStart = min(mergedStart, info.start)
                mergedEnd = max(mergedEnd, info.end)
            } else {
                untouched.append(info)
            }
        }
        return others + untouched + [SpanInfo(start: mergedStart, end: mergedEnd, span: span)]
    }

    /// Removes `span` from `start..<end`, splitting the spans that go beyond the range.
    public static func remove(_ span: String, start: Int, end: Int, from spans: [SpanInfo]) -> [SpanInfo] {
        guard start < end else { return spans }
        return spans.flatMap { info -> [SpanInfo] in
            guard info.span == span, info.end > start, info.start < end else { return [info] }
            var pieces: [SpanInfo] = []
            if info.start < start {
                pieces.append(SpanInfo(start: info.start, end: start, span: span, extra: info.extra))
            }
            if info.end > end {
                pieces.append(SpanInfo(start: end, end: info.end, span: span, extra: info.extra))
            }
            return pieces
        }
    }

    /// Puts a link to `url` on `start..<end`, replacing any link that was there. Links aren't
    /// merged with their neighbours since they may point somewhere else.
    public static func setLink(_ url: String, start: Int, end: Int, in spans: [SpanInfo]) -> [SpanInfo] {
        guard start < end else { return spans }
        return remove(Span.link.rawValue, start: start, end: end, from: spans) +
            [SpanInfo(start: start, end: end, span: Span.link.rawValue, extra: url)]
    }
}
