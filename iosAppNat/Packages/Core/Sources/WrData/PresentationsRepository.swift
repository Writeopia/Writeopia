import Foundation
import WrModels

/// Where the presentations of the documents are read from: the backend in the open space with
/// the cloud AI, the device with the local AI.
public protocol PresentationsRepository: AnyObject {
    /// The presentations of a document with their slides, newest first.
    func presentations(ofDocument documentId: String) async throws -> [Presentation]
    func presentation(id: String) async throws -> Presentation?
    func deletePresentation(id: String) async throws
}

/// A repository on the device, which also keeps the presentations the local AI generates.
public protocol PresentationsStore: PresentationsRepository {
    func savePresentation(_ presentation: Presentation) async throws
}

/// Makes a presentation of a document. The cloud does everything on the server; the local
/// generator asks the model on this machine and parses its answer here.
public protocol PresentationGenerating: AnyObject {
    /// The new presentation, already saved where `PresentationsRepository` reads it.
    func generatePresentation(documentId: String) async throws -> Presentation
}

/// The presentations of a document, as the session resolves them for the AI in use.
public enum PresentationsSource {
    /// The backend generates, parses and keeps the presentations.
    case cloud(PresentationsAPI)
    /// The local AI (Ollama) writes the Markdown and the app parses and keeps the slides.
    case local
}

/// Whatever the AI answers that can't be turned into slides.
public struct PresentationError: Error, Equatable {
    public let message: String

    public init(message: String) {
        self.message = message
    }
}

/// Generates a presentation with the AI on this machine: the same prompt as the backend, the
/// Markdown read by `PresentationMarkdown` and the result saved in the local store.
public final class LocalPresentationGenerator: PresentationGenerating {
    private let aiClient: AiStreaming
    private let store: PresentationsStore
    private let documentTitle: () -> String
    private let documentMarkdown: () -> String

    /// Characters of the document sent to the AI, so a huge document doesn't time out.
    public static let documentLimit = 20_000

    public init(
        aiClient: AiStreaming,
        store: PresentationsStore,
        documentTitle: @escaping () -> String,
        documentMarkdown: @escaping () -> String
    ) {
        self.aiClient = aiClient
        self.store = store
        self.documentTitle = documentTitle
        self.documentMarkdown = documentMarkdown
    }

    public func generatePresentation(documentId: String) async throws -> Presentation {
        let document = String(documentMarkdown().prefix(Self.documentLimit))
        var answer = ""
        for try await partial in aiClient.stream(.prompt, prompt: AiPrompts.presentationPrompt(document: document)) {
            answer = partial
        }
        // A cancelled stream just ends: don't mistake it for an empty answer.
        try Task.checkCancellation()

        let slides = PresentationMarkdown.parse(answer)
        guard !slides.isEmpty else {
            throw PresentationError(message: String(localized: "The AI didn't return any slide."))
        }
        let firstTitle = slides[0].title
        let presentation = Presentation(
            documentId: documentId,
            title: firstTitle.isEmpty ? documentTitle() : firstTitle,
            slides: slides
        )
        try await store.savePresentation(presentation)
        return presentation
    }
}
