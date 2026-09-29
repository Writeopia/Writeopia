import Foundation

/// Reads Markdown (e.g. an AI answer) into steps, like the Markdown parsing of the Compose app:
/// `#`..`####` are headings, `- ` / `* ` bullets, `[] ` / `- [ ] ` / `- [x] ` checklists, and
/// every other non empty line a paragraph. Bold markers are dropped.
public enum MarkdownSteps {
    public static func parse(_ markdown: String, now: Int64 = Date.nowMillis) -> [StoryStep] {
        var steps: [StoryStep] = []

        func add(_ type: StoryType, _ text: String, tag: String? = nil, checked: Bool? = nil) {
            steps.append(StoryStep(
                type: type,
                text: text.replacingOccurrences(of: "**", with: ""),
                checked: checked,
                tags: tag.map { [TagInfo(tag: $0)] } ?? [],
                position: Double(steps.count + 1),
                lastUpdatedAt: now
            ))
        }

        for rawLine in markdown.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            guard !line.isEmpty, !line.hasPrefix("```") else { continue }

            if line.hasPrefix("# ") {
                add(.text, String(line.dropFirst(2)), tag: "H1")
            } else if line.hasPrefix("#### ") {
                add(.text, String(line.dropFirst(5)), tag: "H4")
            } else if line.hasPrefix("### ") {
                add(.text, String(line.dropFirst(4)), tag: "H3")
            } else if line.hasPrefix("## ") {
                add(.text, String(line.dropFirst(3)), tag: "H2")
            } else if line.hasPrefix("- [ ] ") || line.hasPrefix("[] ") {
                add(.checkItem, String(line.drop { $0 != "]" }.dropFirst().drop(while: { $0 == " " })), checked: false)
            } else if line.lowercased().hasPrefix("- [x] ") {
                add(.checkItem, String(line.dropFirst(6)), checked: true)
            } else if line.hasPrefix("- ") || line.hasPrefix("* ") {
                add(.unorderedListItem, String(line.dropFirst(2)))
            } else {
                add(.text, line)
            }
        }
        return steps
    }

    /// The items of a Markdown list (lines starting with `-`, `*` or a number), without their
    /// markers. Used for the list suggestions of the AI.
    public static func listItems(_ markdown: String, limit: Int) -> [String] {
        let items = markdown.components(separatedBy: .newlines).compactMap { rawLine -> String? in
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            let text: Substring
            if line.hasPrefix("- [ ] ") || line.lowercased().hasPrefix("- [x] ") {
                text = line.dropFirst(6)
            } else if line.hasPrefix("- ") || line.hasPrefix("* ") || line.hasPrefix("• ") {
                text = line.dropFirst(2)
            } else if let dot = line.firstIndex(of: "."), line[..<dot].allSatisfy(\.isNumber), !line[..<dot].isEmpty {
                text = line[line.index(after: dot)...]
            } else {
                return nil
            }
            let item = text.replacingOccurrences(of: "**", with: "").trimmingCharacters(in: .whitespaces)
            return item.isEmpty ? nil : item
        }
        return Array(items.prefix(limit))
    }
}
