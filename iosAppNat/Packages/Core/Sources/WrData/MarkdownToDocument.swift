import Foundation
import WrModels

/// Reads Markdown (e.g. an AI answer) into a document, like `MarkdownToDocument` of the Compose
/// app: the first `# ` line is the title, `##`..`####` are headings, `- ` bullets, `[] ` /
/// `- [ ] ` / `- [x] ` checklists, and every other non empty line a paragraph.
public enum MarkdownToDocument {
    public static func read(_ markdown: String, parentId: String, workspaceId: String, fallbackTitle: String = String(localized: "Summary")) -> WrDocument? {
        var title: String?
        var steps: [StoryStep] = []

        func add(_ type: StoryType, _ text: String, tag: String? = nil, checked: Bool? = nil) {
            steps.append(StoryStep(
                type: type,
                text: text,
                checked: checked,
                tags: tag.map { [TagInfo(tag: $0)] } ?? [],
                position: Double(steps.count + 1),
                lastUpdatedAt: Date.nowMillis
            ))
        }

        for rawLine in markdown.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            guard !line.isEmpty, line != "```" else { continue }

            if line.hasPrefix("# ") {
                let text = String(line.dropFirst(2))
                if title == nil { title = text } else { add(.text, text, tag: "H1") }
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
                add(.text, line.replacingOccurrences(of: "**", with: ""))
            }
        }

        guard title != nil || !steps.isEmpty else { return nil }
        let documentTitle = title ?? fallbackTitle
        let now = Date.nowMillis
        return WrDocument(
            id: UUID().uuidString,
            title: documentTitle,
            workspaceId: workspaceId,
            content: [StoryStep(type: .title, text: documentTitle, position: 0, lastUpdatedAt: now)] + steps,
            createdAt: now,
            lastUpdatedAt: now,
            parentId: parentId
        )
    }
}
