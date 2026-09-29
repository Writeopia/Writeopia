import Foundation
import WrModels

/// Reads Markdown (e.g. an AI answer) into a document, like `MarkdownToDocument` of the Compose
/// app: the first `# ` line is the title and the rest is read by `MarkdownSteps`.
public enum MarkdownToDocument {
    public static func read(_ markdown: String, parentId: String, workspaceId: String, fallbackTitle: String = String(localized: "Summary")) -> WrDocument? {
        var steps = MarkdownSteps.parse(markdown)
        var title: String?
        if let titleIndex = steps.firstIndex(where: { $0.hasTag("H1") }) {
            title = steps.remove(at: titleIndex).text
            for index in steps.indices { steps[index].position = Double(index + 1) }
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
