import Foundation
import Observation
import Writeopia
import WriteopiaUI
import WrData
import WrModels
import WrNetwork

public enum ExportFormat: String, CaseIterable, Identifiable {
    case json
    case markdown

    public var id: String { rawValue }

    var fileExtension: String {
        switch self {
        case .json: "json"
        case .markdown: "md"
        }
    }
}

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

    // Menu of the editor (Compose `NoteGlobalActionsMenu`).
    public private(set) var isPublished = false
    public private(set) var isPublishing = false
    public var publishError: String?

    let documentId: String
    private let repository: DocumentsRepository
    @ObservationIgnored private let aiClient: AiStreaming?
    @ObservationIgnored private let publishing: DocumentPublishing?
    @ObservationIgnored private let isPremium: Bool
    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private var aiTask: Task<Void, Never>?
    @ObservationIgnored private var loadedDocument: WrDocument?

    static let fontKey = "wr.editor.font"

    /// `aiClient` and `publishing` are nil in the private space, where there's no backend.
    public init(
        documentId: String,
        repository: DocumentsRepository,
        aiClient: AiStreaming? = nil,
        publishing: DocumentPublishing? = nil,
        isPremium: Bool = false,
        defaults: UserDefaults = .standard,
        writeopiaManager: WriteopiaStateManager = WriteopiaStateManager()
    ) {
        self.documentId = documentId
        self.repository = repository
        self.aiClient = aiClient
        self.publishing = publishing
        self.isPremium = isPremium
        self.defaults = defaults
        self.writeopiaManager = writeopiaManager
        writeopiaManager.fontFamily = defaults.string(forKey: Self.fontKey).flatMap(EditorFont.init(rawValue:)) ?? .system
    }

    // MARK: - Menu

    public var isLocked: Bool { !writeopiaManager.isEditable }

    /// Locks the document against edits, like "Lock document" in the Compose menu.
    public func toggleLock() {
        writeopiaManager.isEditable.toggle()
        if isLocked {
            writeopiaManager.clearLineSelection()
        }
    }

    public var fontFamily: EditorFont { writeopiaManager.fontFamily }

    /// Changes the font of the editor. It's remembered for every document.
    public func changeFontFamily(_ font: EditorFont) {
        writeopiaManager.fontFamily = font
        defaults.set(font.rawValue, forKey: Self.fontKey)
    }

    /// The document as it is now in the editor.
    public var currentDocument: WrDocument {
        let base = loadedDocument ?? WrDocument(id: documentId, title: "", workspaceId: "")
        return WrDocument(
            id: base.id,
            title: writeopiaManager.title,
            workspaceId: base.workspaceId,
            content: writeopiaManager.documentContent,
            createdAt: base.createdAt,
            lastUpdatedAt: base.lastUpdatedAt,
            isFavorite: base.isFavorite,
            parentId: base.parentId
        )
    }

    /// JSON in the format the Compose app shares: the document wrapped in `{"data": ...}`.
    public func exportJson() throws -> String {
        struct Wrapper: Encodable { let data: WrDocument }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        return String(decoding: try encoder.encode(Wrapper(data: currentDocument)), as: UTF8.self)
    }

    public func exportMarkdown() -> String {
        DocumentToMarkdown.parse(writeopiaManager.documentContent)
    }

    /// Writes an export to a temporary file named after the document, ready to be shared.
    public func exportFile(_ format: ExportFormat) throws -> URL {
        let content = switch format {
        case .json: try exportJson()
        case .markdown: exportMarkdown()
        }
        let name = Self.fileName(for: writeopiaManager.title)
        let url = FileManager.default.temporaryDirectory.appending(path: "\(name).\(format.fileExtension)")
        try content.write(to: url, atomically: true, encoding: .utf8)
        return url
    }

    static func fileName(for title: String) -> String {
        let cleaned = title
            .components(separatedBy: CharacterSet.alphanumerics.union(.whitespaces).inverted)
            .joined()
            .trimmingCharacters(in: .whitespaces)
            .replacingOccurrences(of: " ", with: "_")
        return cleaned.isEmpty ? "Untitled" : cleaned
    }

    // MARK: - Publish

    /// Publishing is for premium users in the open space, as in the Compose app.
    public var canPublish: Bool { publishing != nil && isPremium }

    public var siteURL: URL { PublishingAPI.siteURL(documentId: documentId) }

    public func loadPublishState() async {
        guard canPublish, let publishing else { return }
        do {
            isPublished = try await publishing.isPublished(documentId: documentId)
        } catch {
            publishError = error.userMessage
        }
    }

    public func setPublished(_ published: Bool) async {
        guard canPublish, let publishing else { return }
        isPublishing = true
        defer { isPublishing = false }

        do {
            if published {
                try await publishing.publish(documentId: documentId)
            } else {
                try await publishing.unpublish(documentId: documentId)
            }
            isPublished = published
        } catch {
            publishError = error.userMessage
        }
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
            loadedDocument = document
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
