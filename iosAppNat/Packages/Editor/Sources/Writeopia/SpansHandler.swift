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
}
