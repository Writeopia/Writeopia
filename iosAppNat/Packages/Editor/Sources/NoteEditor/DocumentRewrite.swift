import Foundation
import Writeopia
import WrModels

/// A rewrite of the document by the AI, when a command edits the document with nothing
/// selected. The steps with `stepIds` go to the AI as `markdown`, one paragraph per line, and
/// come back whole, rewritten; `parse` turns the answer into steps again. Mirrors
/// `DocumentRewrite` of the Compose app.
///
/// Blocks the AI cannot write, like images, drawings and links, are not sent: a
/// `[[writeopia-block-N]]` line stands for each, and the block itself comes back untouched
/// where the answer leaves the line. The title is never part of the rewrite. A long document is
/// cut to the paragraphs around the cursor that fit `markdownLimit`; the rest stays as it is.
struct DocumentRewrite {
    /// The Markdown sent to the AI is at most this long.
    static let markdownLimit = 20_000

    /// The ids of the steps being rewritten, in document order.
    let stepIds: [String]
    let markdown: String
    /// The steps as they were, to put back when the AI fails.
    let originalSteps: [StoryStep]
    private let keptBlocks: [Int: StoryStep]
    /// Makes the ids of the generated steps, the same from one parse to the next, so the
    /// editor updates the lines in place while the answer streams.
    private let id = UUID().uuidString

    /// The blocks the AI does not see, in their order in the document.
    var keptBlocksInOrder: [StoryStep] { keptBlocks.sorted { $0.key < $1.key }.map(\.value) }

    /// The text of the rewritten steps, for the skeleton that stands in for them.
    var originalText: String {
        originalSteps.compactMap(\.text).filter { !$0.isEmpty }.joined(separator: "\n")
    }

    /// The answer of the AI as steps, in order, and the kept blocks the answer left out.
    struct Parsed {
        let steps: [StoryStep]
        let pendingBlocks: [StoryStep]
    }

    static func placeholder(_ number: Int) -> String { "[[writeopia-block-\(number)]]" }

    /// The types the AI rewrites. Everything else is a kept block, which it does not see.
    private static let rewritable: Set<Int> = [
        StoryType.text.number,
        StoryType.checkItem.number,
        StoryType.unorderedListItem.number,
        StoryType.codeBlock.number,
        StoryType.aiAnswer.number,
        StoryType.divider.number,
    ]

    /// The rewrite of `content` (the document, in order) around the step `cursorStepId`, or
    /// nil when there is nothing to rewrite. The whole document (but the title) when its
    /// Markdown fits `limit`, else the paragraphs around the cursor that do.
    static func build(_ content: [StoryStep], cursorStepId: String?, limit: Int = markdownLimit) -> DocumentRewrite? {
        let entries = content.filter { !$0.isTitle && !StoryTypes.ephemeral.contains($0.type.number) && !$0.isAiSuggestion }
        guard !entries.isEmpty else { return nil }

        let lines = entries.map(markdownLines)
        let sizes = lines.map { $0.reduce(0) { $0 + $1.count + 1 } }

        var low = 0
        var high = entries.count - 1

        if sizes.reduce(0, +) > limit {
            let center = entries.firstIndex { $0.id == cursorStepId } ?? entries.count - 1
            low = center
            high = center
            var size = sizes[center]
            var growBefore = true

            while true {
                let before = low - 1
                let after = high + 1
                let canBefore = before >= 0 && size + sizes[before] <= limit
                let canAfter = after < entries.count && size + sizes[after] <= limit

                if growBefore && canBefore {
                    low = before
                    size += sizes[before]
                } else if canAfter {
                    high = after
                    size += sizes[after]
                } else if canBefore {
                    low = before
                    size += sizes[before]
                } else {
                    break
                }
                growBefore.toggle()
            }
        }

        let region = Array(entries[low...high])
        var kept: [Int: StoryStep] = [:]
        var markdown: [String] = []
        var inCode = false

        for (offset, step) in region.enumerated() {
            let isCode = step.type.number == StoryType.codeBlock.number
            if inCode && !isCode {
                markdown.append("```")
                inCode = false
            }
            if isCode && !inCode {
                markdown.append("```")
                inCode = true
            }

            if rewritable.contains(step.type.number) {
                markdown += lines[low + offset]
            } else {
                let number = kept.count + 1
                kept[number] = step
                markdown.append(placeholder(number))
            }
        }
        if inCode { markdown.append("```") }

        return DocumentRewrite(
            stepIds: region.map(\.id),
            markdown: markdown.joined(separator: "\n"),
            originalSteps: region,
            keptBlocks: kept
        )
    }

    /// Reads `answer`, possibly still incomplete, into steps. The kept blocks keep their ids.
    func parse(_ answer: String) -> Parsed {
        var steps: [StoryStep] = []
        var used: Set<Int> = []
        var buffer: [String] = []

        func flush() {
            guard !buffer.isEmpty else { return }
            steps += MarkdownSteps.parse(buffer.joined(separator: "\n"))
            buffer.removeAll()
        }

        for rawLine in answer.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            if let number = Self.placeholderNumber(line) {
                flush()
                if let block = keptBlocks[number] {
                    used.insert(number)
                    steps.append(block)
                }
            } else {
                buffer.append(rawLine)
            }
        }
        flush()

        let keptIds = Set(keptBlocks.values.map(\.id))
        let named = steps.enumerated().map { index, step in
            keptIds.contains(step.id) ? step : step.withId("\(id)-\(index)")
        }
        let pending = keptBlocks.filter { !used.contains($0.key) }.sorted { $0.key < $1.key }.map(\.value)

        return Parsed(steps: named, pendingBlocks: pending)
    }

    private static func placeholderNumber(_ line: String) -> Int? {
        let prefix = "[[writeopia-block-"
        guard line.hasPrefix(prefix), line.hasSuffix("]]") else { return nil }
        return Int(line.dropFirst(prefix.count).dropLast(2))
    }

    /// The Markdown of `step`, without the fences of a code block, which wrap a run of them.
    private static func markdownLines(_ step: StoryStep) -> [String] {
        let text = step.text ?? ""
        switch step.type.number {
        case _ where !rewritable.contains(step.type.number):
            return [placeholder(0)]
        case StoryType.divider.number:
            return ["---"]
        case StoryType.codeBlock.number:
            return [text]
        case StoryType.checkItem.number:
            return ["- [\(step.checked == true ? "x" : " ")] \(text)"]
        case StoryType.unorderedListItem.number:
            return ["- \(text)"]
        default:
            if let level = step.headingLevel {
                return ["\(String(repeating: "#", count: level)) \(text)"]
            }
            return [text.replacingOccurrences(of: "\n", with: " ")]
        }
    }
}

extension StoryStep {
    /// The same step with another id.
    func withId(_ id: String) -> StoryStep {
        StoryStep(
            id: id,
            type: type,
            text: text,
            checked: checked,
            url: url,
            path: path,
            steps: steps,
            tags: tags,
            spans: spans,
            position: position,
            documentLink: documentLink,
            parentId: parentId,
            decoration: decoration,
            lastUpdatedAt: lastUpdatedAt
        )
    }
}
