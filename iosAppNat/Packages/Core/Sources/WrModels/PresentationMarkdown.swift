import Foundation

/// Reads the Markdown the AI writes for a presentation into slides: every `---` line ends a
/// slide, and the first heading of a slide is its title. The lines of each slide are read by
/// `MarkdownSteps`, so lists, checkboxes and code blocks come out as in a document.
public enum PresentationMarkdown {
    public static func parse(_ markdown: String, now: Int64 = Date.nowMillis) -> [Slide] {
        let lines = unwrapped(markdown.components(separatedBy: .newlines))
        let chunks = lines.contains(where: isDivider) ? split(lines, at: isDivider) : split(lines, before: isHeading)

        return chunks.compactMap { chunk -> Slide? in
            var steps = MarkdownSteps.parse(joinParagraphs(chunk).joined(separator: "\n"), now: now)
            var title = ""
            if let headingIndex = steps.firstIndex(where: isHeading) {
                title = steps.remove(at: headingIndex).text ?? ""
            }
            guard !title.isEmpty || !steps.isEmpty else { return nil }
            for index in steps.indices {
                steps[index].position = Double(index + 1)
            }
            return Slide(title: title, steps: steps)
        }
    }

    /// Joins the lines of a paragraph into one, as Markdown reads a single line break: models
    /// often wrap their prose, and every line would otherwise be a paragraph of its own. Blank
    /// lines, headings, list items, checkboxes, dividers and code blocks keep their own lines.
    static func joinParagraphs(_ lines: [String]) -> [String] {
        var result: [String] = []
        var paragraph: String?
        var openFence: MarkdownSteps.Fence?

        func flush() {
            if let paragraph { result.append(paragraph) }
            paragraph = nil
        }

        for rawLine in lines {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            if let fence = openFence {
                result.append(rawLine)
                if fence.isClosed(by: line) { openFence = nil }
                continue
            }
            if let fence = MarkdownSteps.Fence(opening: line) {
                flush()
                openFence = fence
                result.append(rawLine)
            } else if line.isEmpty {
                flush()
            } else if isBlock(line) {
                flush()
                result.append(line)
            } else if let current = paragraph {
                paragraph = current + " " + line
            } else {
                paragraph = line
            }
        }
        flush()
        return result
    }

    /// A line `MarkdownSteps` reads as a block of its own, never part of a paragraph.
    private static func isBlock(_ line: String) -> Bool {
        line.hasPrefix("#") || isDivider(line) ||
            line.hasPrefix("- ") || line.hasPrefix("* ") ||
            line.hasPrefix("[] ") || line.hasPrefix("- [ ] ") || line.lowercased().hasPrefix("- [x] ")
    }

    /// A line of three or more dashes and nothing else, the end of a slide.
    static func isDivider(_ rawLine: String) -> Bool {
        let line = rawLine.trimmingCharacters(in: .whitespaces)
        return line.count >= 3 && line.allSatisfy { $0 == "-" }
    }

    private static func isHeading(_ rawLine: String) -> Bool {
        let line = rawLine.trimmingCharacters(in: .whitespaces)
        return ["# ", "## ", "### ", "#### "].contains { line.hasPrefix($0) }
    }

    private static func isHeading(_ step: StoryStep) -> Bool {
        ["H1", "H2", "H3", "H4"].contains(where: step.hasTag)
    }

    /// Models often wrap the whole answer in a ```markdown fence; without this every line would
    /// become a code block.
    private static func unwrapped(_ lines: [String]) -> [String] {
        var lines = lines
        while let last = lines.last, last.trimmingCharacters(in: .whitespaces).isEmpty { lines.removeLast() }
        while let first = lines.first, first.trimmingCharacters(in: .whitespaces).isEmpty { lines.removeFirst() }
        guard lines.count >= 2,
              let first = lines.first?.trimmingCharacters(in: .whitespaces), first.hasPrefix("```"),
              let last = lines.last?.trimmingCharacters(in: .whitespaces), last == "```"
        else { return lines }
        return Array(lines.dropFirst().dropLast())
    }

    private static func split(_ lines: [String], at isSeparator: (String) -> Bool) -> [[String]] {
        var chunks: [[String]] = [[]]
        for line in lines {
            if isSeparator(line) {
                chunks.append([])
            } else {
                chunks[chunks.count - 1].append(line)
            }
        }
        return chunks
    }

    private static func split(_ lines: [String], before startsChunk: (String) -> Bool) -> [[String]] {
        var chunks: [[String]] = [[]]
        for line in lines {
            if startsChunk(line), !chunks[chunks.count - 1].isEmpty {
                chunks.append([line])
            } else {
                chunks[chunks.count - 1].append(line)
            }
        }
        return chunks
    }
}
