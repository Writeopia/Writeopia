import Foundation
import OSLog
import Writeopia
import WriteopiaUI
import WrData
import WrModels

/// Applies the suggestions to the document, like `WriteopiaActionExecutor` of the Kotlin app.
/// Blocks are inserted right below the paragraph the suggestion is about, except the section
/// heading, which goes above it, the TL;DR, which goes under the title, and the conclusion, which
/// goes at the end. The document title is written into the title block itself.
///
/// The actions that need text use the AI the user picked, through `resolveAi`. The image is drawn
/// by the cloud, through `resolveImageAi`, with the whole document as context; without it the
/// image picker opens instead. `activity` says what runs, for the chip of the editor, and `error`
/// why an action failed.
@Observable
public final class WritingActionExecutor {
    /// What the AI is doing right now, shown by the editor; nil when idle.
    public private(set) var activity: String?
    public var error: String?

    @ObservationIgnored let manager: WriteopiaStateManager
    @ObservationIgnored private let resolveAi: () -> AiStreaming?
    @ObservationIgnored private let resolveImageAi: () -> AiImageGenerating?
    @ObservationIgnored private let createDocument: (String) async throws -> (id: String, title: String)
    @ObservationIgnored private var task: Task<Void, Never>?

    static let logger = Logger(subsystem: "io.writeopia.WriteopiaNative", category: "writingAgent")

    /// How much of the document goes with the image request, so the picture fits the text.
    static let imageContextLimit = 20_000
    static let maxListItems = 10

    public init(
        manager: WriteopiaStateManager,
        resolveAi: @escaping () -> AiStreaming?,
        resolveImageAi: @escaping () -> AiImageGenerating? = { nil },
        createDocument: @escaping (String) async throws -> (id: String, title: String)
    ) {
        self.manager = manager
        self.resolveAi = resolveAi
        self.resolveImageAi = resolveImageAi
        self.createDocument = createDocument
    }

    public var isRunning: Bool { activity != nil }

    public func cancel() {
        task?.cancel()
    }

    /// Applies `action` to the step with `stepId`; the result is awaited, so the controller and
    /// the tests know when the document changed.
    public func execute(_ action: WritingSuggestionAction, stepId: String?, ui: WritingAgentUI) async {
        Self.logger.info("executing \(action.rawValue, privacy: .public) for \(stepId ?? "-", privacy: .public)")

        switch action {
        case .codeBlock:
            await run(String(localized: "Writing the code snippet…")) { try await self.codeSnippet(stepId: stepId) }
        case .list:
            await run(String(localized: "Writing the list…")) {
                try await self.listItems(stepId: stepId, type: .unorderedListItem, instructions: Self.listPrompt)
            }
        case .checkList:
            await run(String(localized: "Writing the checklist…")) {
                try await self.listItems(stepId: stepId, type: .checkItem, instructions: Self.checklistPrompt)
            }
        case .newDocumentLink:
            await run(nil) { try await self.linkedDocument(stepId: stepId) }
        case .image:
            if resolveImageAi() == nil {
                Self.logger.info("the cloud AI can't draw right now, opening the image picker")
                ui.pickImage()
            } else {
                await run(String(localized: "Drawing the image…")) { try await self.generatedImage(stepId: stepId) }
            }
        case .drawing:
            ui.openDrawing()
        case .spreadsheet:
            // This editor doesn't draw spreadsheets yet; the suggestion is never shown.
            break
        case .sectionHeading:
            await run(String(localized: "Writing a section heading…")) { try await self.heading(stepId: stepId) }
        case .documentTitle:
            await run(String(localized: "Writing the document title…")) { try await self.documentTitle() }
        case .callout:
            if let stepId { manager.toggleTag(.box, stepId: stepId) }
        case .tldr:
            await run(String(localized: "Writing a TL;DR…")) { try await self.tldr() }
        case .conclusion:
            await run(String(localized: "Writing a conclusion…")) { try await self.conclusion() }
        }
    }

    /// Runs `block` as the current activity, like a task of the Kotlin `AiTaskManager`: one at a
    /// time, with the failure kept in `error` so the user learns why nothing changed.
    private func run(_ description: String?, _ block: @escaping () async throws -> Void) async {
        guard task == nil else {
            Self.logger.info("an action is still running; \(description ?? "action", privacy: .public) skipped")
            return
        }
        activity = description ?? String(localized: "Applying the suggestion…")
        let running = Task {
            do {
                try await block()
            } catch is CancellationError {
                Self.logger.info("\(description ?? "action", privacy: .public) cancelled")
            } catch {
                Self.logger.error("\(description ?? "action", privacy: .public) failed: \(error.localizedDescription, privacy: .public)")
                self.error = Self.message(of: error)
            }
        }
        task = running
        await running.value
        task = nil
        activity = nil
    }

    static func message(of error: Error) -> String {
        if let error = error as? AiStreamError { return error.message }
        if let error = error as? WritingAgentError { return error.message }
        return error.userMessage
    }

    private func ai() throws -> AiStreaming {
        guard let ai = resolveAi() else { throw WritingAgentError.noAi }
        return ai
    }

    /// The whole answer of the AI, once the stream ends.
    static func finalText(_ ai: AiStreaming, prompt: String) async throws -> String {
        var text = ""
        for try await partial in ai.stream(.prompt, prompt: prompt) {
            text = partial
        }
        let answer = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !answer.isEmpty else { throw WritingAgentError.emptyAnswer }
        return answer
    }

    /// One line of text from the AI, cleaned of the Markdown and quotes it tends to add.
    static func oneLine(_ ai: AiStreaming, instructions: String, text: String) async throws -> String {
        let answer = try await finalText(ai, prompt: "\(instructions)\n```\n\(text)\n```")
        guard let line = answer.components(separatedBy: .newlines).first(where: { !$0.trimmingCharacters(in: .whitespaces).isEmpty }) else {
            throw WritingAgentError.emptyAnswer
        }
        return line.trimmingCharacters(in: CharacterSet(charactersIn: "# \"*."))
    }

    private func paragraph(_ stepId: String?) -> String {
        stepId.flatMap { manager.step(withId: $0)?.text } ?? ""
    }

    private func context(_ stepId: String?) -> String {
        "Title: \(manager.title)\nParagraph: \(paragraph(stepId))\n\nDocument:\n\(manager.documentText)"
    }

    // MARK: - Actions

    /// The code the paragraph calls for, one code block per line, like the editor makes them.
    private func codeSnippet(stepId: String?) async throws {
        let answer = try await Self.finalText(ai(), prompt: "\(Self.codePrompt)\n```\n\(context(stepId))\n```")
        let code = MarkdownCode.parse(answer)
        guard !code.isEmpty else { throw WritingAgentError.emptyAnswer }

        let lines = code.components(separatedBy: "\n").map { StoryStep(type: .codeBlock, text: $0, position: Rank.first) }
        Self.logger.info("inserting \(lines.count) code block lines below \(stepId ?? "-", privacy: .public)")
        manager.addSteps(lines, afterStepId: stepId)
    }

    /// The items the paragraph calls for, one block each, as `type`.
    private func listItems(stepId: String?, type: StoryType, instructions: String) async throws {
        let context = "Title: \(manager.title)\nParagraph: \(paragraph(stepId))"
        let answer = try await Self.finalText(ai(), prompt: "\(instructions)\n```\n\(context)\n```")
        let items = MarkdownSteps.listItems(answer, limit: Self.maxListItems)
        guard !items.isEmpty else { throw WritingAgentError.emptyAnswer }

        let steps = items.map { item in
            StoryStep(type: type, text: item, checked: type.number == StoryType.checkItem.number ? false : nil, position: Rank.first)
        }
        Self.logger.info("inserting \(steps.count) \(type.name, privacy: .public) items below \(stepId ?? "-", privacy: .public)")
        manager.addSteps(steps, afterStepId: stepId)
    }

    /// A new document named after the paragraph, linked right below it.
    private func linkedDocument(stepId: String?) async throws {
        let title = paragraph(stepId).trimmingCharacters(in: .whitespacesAndNewlines)
        let page = try await createDocument(title.isEmpty ? "Untitled" : String(title.prefix(60)))
        manager.addDocumentLink(afterStepId: stepId, documentId: page.id, title: page.title)
    }

    /// A picture of the paragraph, drawn by the cloud with the whole document as context, in
    /// place of a loading block shown while it draws.
    private func generatedImage(stepId: String?) async throws {
        guard let generator = resolveImageAi() else { throw WritingAgentError.noAi }
        let paragraph = paragraph(stepId).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !paragraph.isEmpty else { throw WritingAgentError.noParagraph }

        let loadingId = manager.loadingAtPosition(stepId.flatMap { manager.position(of: $0) } ?? manager.lastPosition)
        do {
            let url = try await generator.generateImage(
                paragraph: paragraph,
                documentTitle: manager.title,
                documentText: String(manager.documentText.prefix(Self.imageContextLimit))
            )
            Self.logger.info("inserting the image below \(stepId ?? "-", privacy: .public)")
            manager.replaceStep(StoryStep(id: loadingId, type: .image, url: url, position: Rank.first))
        } catch {
            manager.removeStep(stepId: loadingId)
            throw error
        }
    }

    private func heading(stepId: String?) async throws {
        guard let stepId else { return }
        let title = try await Self.oneLine(ai(), instructions: Self.headingPrompt, text: paragraph(stepId))
        manager.addStep(StoryStep(type: .text, text: title, tags: [TagInfo(tag: BlockTag.h2.rawValue)], position: Rank.first), beforeStepId: stepId)
    }

    private func documentTitle() async throws {
        let title = try await Self.oneLine(ai(), instructions: Self.documentTitlePrompt, text: manager.documentText)
        Self.logger.info("the AI titled the document \"\(title, privacy: .public)\"")
        manager.setTitle(title)
    }

    /// A card under the title, starting with "TLDR: ", like a hand-written one.
    private func tldr() async throws {
        let ai = try ai()
        let loading = StoryStep(type: .loading, position: Rank.first)
        manager.addStepAfterTitle(loading)
        let prompt = "\(Self.tldrPrompt)\n```\n\(manager.documentText)\n```"
        do {
            var received = false
            for try await partial in ai.stream(.prompt, prompt: prompt) {
                received = true
                manager.replaceStep(StoryStep(
                    id: loading.id,
                    type: .text,
                    text: Self.tldrPrefix + partial.trimmingCharacters(in: .whitespacesAndNewlines),
                    tags: [TagInfo(tag: BlockTag.card.rawValue)],
                    position: Rank.first
                ))
            }
            if !received { throw WritingAgentError.emptyAnswer }
        } catch {
            manager.removeStep(stepId: loading.id)
            throw error
        }
    }

    /// An AI answer at the end of the document.
    private func conclusion() async throws {
        let ai = try ai()
        let answerId = manager.loadingAtPosition(manager.lastPosition)
        let prompt = "\(Self.conclusionPrompt)\n```\n\(manager.documentText)\n```"
        do {
            var received = false
            for try await partial in ai.stream(.prompt, prompt: prompt) {
                received = true
                manager.showAiAnswer(partial, stepId: answerId)
            }
            manager.aiAnswerFinished(stepId: answerId)
            if !received { throw WritingAgentError.emptyAnswer }
        } catch {
            manager.removeStep(stepId: answerId)
            throw error
        }
    }

    // MARK: - Prompts, the same as the Kotlin app

    static let tldrPrefix = "TLDR: "

    static let codePrompt =
        "The paragraph below introduces or describes a piece of code, a command or a configuration snippet. Write that snippet, complete and ready to use, in the language or tool the paragraph implies (use the rest of the document to decide when the paragraph doesn't say). Answer only with the code, inside one fenced code block, with no explanation before or after it. Comments in the code should use the language of the text."

    static let listPrompt =
        "The paragraph below introduces or enumerates several items. Write those items as a bullet list, completing the list with the items the paragraph implies. Answer only with the items, one per line, each starting with \"- \". At most ten items, each a short phrase. Use the language of the text."

    static let checklistPrompt =
        "The paragraph below describes tasks or steps to complete. Write them as a checklist, one task per line, each starting with \"- \", in the order they should be done. Answer only with the tasks. At most ten, each a short phrase starting with a verb. Use the language of the text."

    static let tldrPrompt =
        "Write a TL;DR for the following document: at most three sentences with its key points. Answer only with the sentences: no title, no \"TL;DR\" label, no Markdown. Use the language of the text."

    static let conclusionPrompt =
        "Write a conclusion for the following document: one or two paragraphs that wrap up its ideas. Answer only with the conclusion, without a title. Use the language of the text."

    static let headingPrompt =
        "Write a short heading, of at most eight words, for the following paragraph. Answer only with the heading: no quotes, no Markdown, no punctuation at the end. Use the language of the text."

    static let documentTitlePrompt =
        "Write a title for the following document, of at most eight words. Answer only with the title: no quotes, no Markdown, no punctuation at the end. Use the language of the text."

    static let folderNamePrompt =
        "Name a folder that would hold the following document and others like it: a broad category of at most three words, like \"Meetings\", \"Recipes\" or \"Project Atlas\", not a title for this one document. Answer only with the name: no quotes, no Markdown, no punctuation. Use the language of the text."
}

/// Why an action of the writing agent couldn't be applied.
public enum WritingAgentError: Error, Equatable {
    case noAi
    case emptyAnswer
    case noParagraph

    public var message: String {
        switch self {
        case .noAi: String(localized: "No AI is available. Pick one in the settings, or sign in to use the cloud AI.")
        case .emptyAnswer: String(localized: "The AI answered nothing.")
        case .noParagraph: String(localized: "There is no paragraph to illustrate.")
        }
    }
}
