import Foundation
import Observation
import WrData
import WrModels
import WrNetwork

/// The presentations of a document: the ones generated before, and the generation of a new
/// one by the AI. The AI writes the slides as Markdown, read by `PresentationMarkdown`, and
/// the result is kept on the device.
@Observable
public final class PresentationsViewModel {
    public private(set) var presentations: [Presentation] = []
    public private(set) var isLoading = false
    public private(set) var isGenerating = false
    public var error: String?

    public let documentId: String
    @ObservationIgnored private let documentTitle: () -> String
    @ObservationIgnored private let documentMarkdown: () -> String
    @ObservationIgnored private let repository: PresentationsRepository
    @ObservationIgnored private let aiClient: AiStreaming
    @ObservationIgnored private var generationTask: Task<Presentation?, Never>?

    /// Characters of the document sent to the AI. The cloud and Ollama take long inputs; this
    /// keeps a huge document from timing out.
    static let documentLimit = 20_000

    public init(
        documentId: String,
        documentTitle: @escaping () -> String,
        documentMarkdown: @escaping () -> String,
        repository: PresentationsRepository,
        aiClient: AiStreaming
    ) {
        self.documentId = documentId
        self.documentTitle = documentTitle
        self.documentMarkdown = documentMarkdown
        self.repository = repository
        self.aiClient = aiClient
    }

    public func load() async {
        isLoading = true
        defer { isLoading = false }
        do {
            presentations = try await repository.presentations(ofDocument: documentId)
        } catch {
            self.error = error.userMessage
        }
    }

    /// Asks the AI for the slides of the document, saves them and returns the new presentation;
    /// nil when it failed or was cancelled, with the reason in `error`.
    @discardableResult
    public func generate() async -> Presentation? {
        guard !isGenerating else { return nil }
        isGenerating = true
        defer {
            isGenerating = false
            generationTask = nil
        }
        let task = Task { await generatePresentation() }
        generationTask = task
        return await task.value
    }

    private func generatePresentation() async -> Presentation? {
        let document = String(documentMarkdown().prefix(Self.documentLimit))
        let prompt = AiPrompts.presentationPrompt(document: document)
        do {
            var answer = ""
            for try await partial in aiClient.stream(.prompt, prompt: prompt) {
                answer = partial
            }
            // A cancelled stream just ends: don't mistake it for an empty answer.
            try Task.checkCancellation()

            let slides = PresentationMarkdown.parse(answer)
            guard !slides.isEmpty else {
                error = String(localized: "The AI didn't return any slide.")
                return nil
            }
            let firstTitle = slides[0].title
            let presentation = Presentation(
                documentId: documentId,
                title: firstTitle.isEmpty ? documentTitle() : firstTitle,
                slides: slides
            )
            try await repository.savePresentation(presentation)
            await load()
            return presentation
        } catch is CancellationError {
            return nil
        } catch let error as AiStreamError {
            self.error = error.message
            return nil
        } catch {
            if Task.isCancelled { return nil }
            self.error = error.userMessage
            return nil
        }
    }

    /// Stops the generation in progress; nothing is saved.
    public func cancel() {
        generationTask?.cancel()
    }

    public func delete(_ presentation: Presentation) async {
        do {
            try await repository.deletePresentation(id: presentation.id)
            presentations.removeAll { $0.id == presentation.id }
        } catch {
            self.error = error.userMessage
        }
    }
}
