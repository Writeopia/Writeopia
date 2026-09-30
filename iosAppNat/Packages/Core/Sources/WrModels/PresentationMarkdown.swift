import Foundation

/// Reads the Markdown the AI writes for a presentation into slides: every `---` line ends a
/// slide, and the first heading of a slide is its title. The lines of each slide are read by
/// `MarkdownSteps`, so lists, checkboxes and code blocks come out as in a document.
public enum PresentationMarkdown {
    public static func parse(_ markdown: String, now: Int64 = Date.nowMillis) -> [Slide] {
        let lines = unwrapped(markdown.components(separatedBy: .newlines))
        let chunks = lines.contains(where: isDivider) ? split(lines, at: isDivider) : split(lines, before: isHeading)

        return chunks.compactMap { chunk -> Slide? in
            var steps = MarkdownSteps.parse(chunk.joined(separator: "\n"), now: now)
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
