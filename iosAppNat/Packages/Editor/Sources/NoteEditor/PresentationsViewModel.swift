import Foundation
import Observation
import WrData
import WrModels
import WrNetwork

/// The presentations of a document: the ones made before, and the making of a new one. Who
/// makes and keeps them depends on the AI in use (`PresentationsSource`): the backend with the
/// cloud AI, this device with the local AI. Either way the app only shows what it gets back.
@Observable
public final class PresentationsViewModel {
    public private(set) var presentations: [Presentation] = []
    public private(set) var isLoading = false
    public private(set) var isGenerating = false
    public var error: String?

    public let documentId: String
    @ObservationIgnored private let repository: PresentationsRepository
    @ObservationIgnored private let generator: PresentationGenerating
    /// Runs before asking for a presentation, e.g. to send the pending edits to the backend so
    /// the slides come from the latest text.
    @ObservationIgnored private let beforeGenerate: () async -> Void
    @ObservationIgnored private var generationTask: Task<Presentation?, Never>?

    public init(
        documentId: String,
        repository: PresentationsRepository,
        generator: PresentationGenerating,
        beforeGenerate: @escaping () async -> Void = {}
    ) {
        self.documentId = documentId
        self.repository = repository
        self.generator = generator
        self.beforeGenerate = beforeGenerate
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

    /// Makes a new presentation of the document and returns it; nil when it failed or was
    /// cancelled, with the reason in `error`.
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
        do {
            await beforeGenerate()
            try Task.checkCancellation()
            let presentation = try await generator.generatePresentation(documentId: documentId)
            await load()
            return presentation
        } catch is CancellationError {
            return nil
        } catch let error as PresentationError {
            self.error = error.message
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

    /// Stops the generation in progress; nothing is kept.
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
