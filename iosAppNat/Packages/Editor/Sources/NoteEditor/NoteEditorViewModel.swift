import Foundation
import Observation
import Writeopia
import WriteopiaUI
import WrData
import WrModels
import WrNetwork

/// What an AI command reads: the whole document or the step with the cursor.
/// Mirrors `AiTargetMode` of the Compose editor (selected lines aren't available on mobile).
public enum AiTargetMode: String, CaseIterable, Identifiable {
    case document
    case cursor

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .document: "Document"
        case .cursor: "Cursor"
        }
    }
}

/// Loads a document into the editor and runs the AI commands. Counterpart of
/// `NoteEditorKmpViewModel`: changes are kept in memory and not saved yet.
@Observable
public final class NoteEditorViewModel {
    public let writeopiaManager: WriteopiaStateManager
    public private(set) var isLoading = false
    public private(set) var errorMessage: String?
    public private(set) var hasLoaded = false
    public private(set) var isAiRunning = false

    private let documentId: String
    private let repository: DocumentsRepository
    @ObservationIgnored private let aiClient: AiStreaming?
    @ObservationIgnored private var aiTask: Task<Void, Never>?

    /// `aiClient` is nil in the private space, where cloud AI isn't available.
    public init(
        documentId: String,
        repository: DocumentsRepository,
        aiClient: AiStreaming? = nil,
        writeopiaManager: WriteopiaStateManager = WriteopiaStateManager()
    ) {
        self.documentId = documentId
        self.repository = repository
        self.aiClient = aiClient
        self.writeopiaManager = writeopiaManager
    }

    public var isAiAvailable: Bool { aiClient != nil }

    /// Commands offered for each target, like the AI dialog of the Compose editor: the cursor
    /// only supports a free prompt.
    public static func commands(for mode: AiTargetMode) -> [AiCommand] {
        switch mode {
        case .document: AiCommand.allCases
        case .cursor: [.prompt]
        }
    }

    /// Sends the text of `mode` to the AI and streams the answer into the document, below the
    /// text it was based on. A loading step shows until the first part of the answer arrives.
    public func runAi(_ command: AiCommand, mode: AiTargetMode) {
        guard let aiClient else { return }

        let input: (text: String, position: Double?)?
        switch mode {
        case .document:
            input = (writeopiaManager.documentText, writeopiaManager.lastPosition)
        case .cursor:
            input = writeopiaManager.currentTextStep.map { ($0.step.text ?? "", $0.position) }
        }

        guard let input, !input.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }

        let answerId = writeopiaManager.loadingAtPosition(input.position)
        isAiRunning = true

        aiTask = Task { [weak self] in
            var receivedAnswer = false
            do {
                for try await answer in aiClient.stream(command, prompt: input.text) {
                    receivedAnswer = true
                    self?.writeopiaManager.showAiAnswer(answer, stepId: answerId)
                }
                if !receivedAnswer {
                    self?.writeopiaManager.removeStep(stepId: answerId)
                }
            } catch is CancellationError {
                if !receivedAnswer { self?.writeopiaManager.removeStep(stepId: answerId) }
            } catch let error as AiStreamError {
                self?.writeopiaManager.showAiAnswer("Error. Message: \(error.message)", stepId: answerId)
            } catch {
                self?.writeopiaManager.showAiAnswer("Error. Message: \(error.userMessage)", stepId: answerId)
            }
            self?.isAiRunning = false
        }
    }

    public func cancelAi() {
        aiTask?.cancel()
    }

    /// Title as it is being typed.
    public var title: String { writeopiaManager.title }

    public func loadDocument() async {
        // Reloading would throw away the edits, which are only in memory.
        guard !hasLoaded else { return }

        isLoading = true
        defer { isLoading = false }

        do {
            let document = try await repository.document(id: documentId)
            writeopiaManager.loadDocument(document)
            hasLoaded = true
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.userMessage
        }
    }
}
