import Foundation

/// Reads Markdown (e.g. an AI answer) into steps, like the Markdown parsing of the Compose app:
/// `#`..`####` are headings, `- ` / `* ` bullets, `[] ` / `- [ ] ` / `- [x] ` checklists, and
/// every other non empty line a paragraph. Bold markers are dropped. Lines inside a fenced code
/// block are kept as they are, one code block step each.
public enum MarkdownSteps {
    public static func parse(_ markdown: String, now: Int64 = Date.nowMillis) -> [StoryStep] {
        var steps: [StoryStep] = []

        func add(_ type: StoryType, _ text: String, tag: String? = nil, checked: Bool? = nil) {
            steps.append(StoryStep(
                type: type,
                text: type.number == StoryType.codeBlock.number ? text : text.replacingOccurrences(of: "**", with: ""),
                checked: checked,
                tags: tag.map { [TagInfo(tag: $0)] } ?? [],
                position: Double(steps.count + 1),
                lastUpdatedAt: now
            ))
        }

        // The fence that opened the code block being read, if any.
        var openFence: Fence?

        for rawLine in markdown.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)

            if let fence = openFence {
                if fence.isClosed(by: line) {
                    openFence = nil
                } else {
                    add(.codeBlock, rawLine)
                }
                continue
            }
            if let fence = Fence(opening: line) {
                openFence = fence
                continue
            }
            guard !line.isEmpty else { continue }

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

    /// A ``` or ~~~ fence of a code block.
    struct Fence {
        let character: Character
        let length: Int

        init?(opening line: String) {
            guard let first = line.first, first == "`" || first == "~" else { return nil }
            let length = line.prefix { $0 == first }.count
            guard length >= 3 else { return nil }
            character = first
            self.length = length
        }

        /// Only a fence of the same character, at least as long and with nothing after it, closes.
        func isClosed(by line: String) -> Bool {
            line.count >= length && line.allSatisfy { $0 == character }
        }
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
