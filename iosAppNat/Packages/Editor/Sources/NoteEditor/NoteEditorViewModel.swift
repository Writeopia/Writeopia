import Drawing
import Foundation
import Observation
import Writeopia
import WriteopiaUI
import WrData
import WrModels
import WrNetwork

/// Where copied lines go; the system pasteboard in the app, a fake in tests.
public protocol LineClipboard {
    func copy(_ lines: [StoryStep])
}

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
    /// The lines selected by sliding them. Only offered from the selection menu, like the
    /// Compose app does on mobile.
    case selectedLines

    public var id: String { rawValue }

    /// Targets the user can pick in the AI dialog opened from the regular menu.
    public static let pickable: [AiTargetMode] = [.document, .cursor]

    public var title: String {
        switch self {
        case .document: String(localized: "Document")
        case .cursor: String(localized: "Cursor")
        case .selectedLines: String(localized: "Selected lines")
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
    public var linkError: String?
    public var imageError: String?

    let documentId: String
    private let repository: DocumentsRepository
    @ObservationIgnored private let aiClient: AiStreaming?
    @ObservationIgnored private let publishing: DocumentPublishing?
    @ObservationIgnored private let imageUploader: ImageUploading?
    @ObservationIgnored private let isPremium: Bool
    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private var aiTask: Task<Void, Never>?
    @ObservationIgnored private var suggestionTask: Task<Void, Never>?
    @ObservationIgnored private var loadedDocument: WrDocument?

    // Persistence and sync, like `registerForSync` of the Compose editor.
    @ObservationIgnored private var syncing: DocumentSyncing? { repository as? DocumentSyncing }
    /// Steps as last saved on the device, to find what an edit changed.
    @ObservationIgnored private var savedSteps: [String: StoryStep] = [:]
    /// Steps as last sent to the backend.
    @ObservationIgnored private var pushedSteps: [String: StoryStep] = [:]
    @ObservationIgnored private var lastSyncTimestamp: Int64 = 0
    @ObservationIgnored private var changeCountAtLoad = 0
    @ObservationIgnored private var saveTask: Task<Void, Never>?
    @ObservationIgnored private var pushTask: Task<Void, Never>?
    public private(set) var isSyncing = false
    /// The presentations of the document; nil when the AI in use can't generate them (only the
    /// cloud AI and Ollama do) or the repository can't keep them.
    public private(set) var presentations: PresentationsViewModel?
    /// Set once the document is deleted: nothing is saved or sent anymore.
    public private(set) var isDeleted = false
    public var deleteError: String?
    public private(set) var lastSyncFailed = false

    /// Delay after the last edit before sending to the backend. Saving on the device happens on
    /// every edit, like the Compose app.
    public var pushDelay: Duration = .seconds(2)

    static let fontKey = "wr.editor.font"

    /// `aiClient` and `publishing` are nil in the private space, where there's no backend.
    /// `presentationsEnabled` is true when the AI in use can write presentations.
    public init(
        documentId: String,
        repository: DocumentsRepository,
        aiClient: AiStreaming? = nil,
        publishing: DocumentPublishing? = nil,
        imageUploader: ImageUploading? = nil,
        isPremium: Bool = false,
        presentationsEnabled: Bool = false,
        defaults: UserDefaults = .standard,
        writeopiaManager: WriteopiaStateManager = WriteopiaStateManager()
    ) {
        self.documentId = documentId
        self.repository = repository
        self.aiClient = aiClient
        self.publishing = publishing
        self.imageUploader = imageUploader
        self.isPremium = isPremium
        self.defaults = defaults
        self.writeopiaManager = writeopiaManager
        writeopiaManager.fontFamily = defaults.string(forKey: Self.fontKey).flatMap(EditorFont.init(rawValue:)) ?? .system
        writeopiaManager.customDrawableTypes = [StoryType.drawing.number]

        if aiClient != nil {
            writeopiaManager.onGenerateSection = { [weak self] stepId in self?.generateSection(stepId: stepId) }
            writeopiaManager.onListStarted = { [weak self] stepId in self?.suggestListItems(after: stepId) }
        }
        if presentationsEnabled, let aiClient, let presentationsRepository = repository as? PresentationsRepository {
            presentations = PresentationsViewModel(
                documentId: documentId,
                documentTitle: { [weak self] in self?.writeopiaManager.title ?? "" },
                documentMarkdown: { [weak self] in self?.exportMarkdown() ?? "" },
                repository: presentationsRepository,
                aiClient: aiClient
            )
        }
    }

    public var showsPresentations: Bool { presentations != nil }

    // MARK: - Selected lines

    /// Copies the selected lines, with their formatting, like `copySelection` of the Compose app.
    public func copySelectedLines(to pasteboard: LineClipboard) {
        let lines = writeopiaManager.selectedLines.filter { $0.text != nil }
        guard !lines.isEmpty else { return }
        pasteboard.copy(lines)
    }

    public func cutSelectedLines(to pasteboard: LineClipboard) {
        copySelectedLines(to: pasteboard)
        writeopiaManager.deleteSelectedLines()
    }

    /// Creates a document named after the last selected line and links it right below, like
    /// "Link to page" of the Compose selection menu.
    public func linkSelectionToNewPage() async {
        guard writeopiaManager.isEditable, let last = writeopiaManager.selectedLines.last else { return }
        let title = (last.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)

        do {
            let page = try await repository.createDocument(
                title: title.isEmpty ? "Untitled" : title,
                parentId: loadedDocument?.parentId ?? Folder.rootId
            )
            writeopiaManager.addDocumentLinkAfterSelection(documentId: page.id, title: page.displayTitle)
        } catch {
            linkError = error.userMessage
        }
    }

    // MARK: - Images

    /// Adds a picked image: it's saved on the device and shown right away, then uploaded in the
    /// open space so other devices can see it, like `addImage` of the Compose app. When the
    /// upload fails the image stays local.
    public func addImage(_ data: Data) async {
        guard writeopiaManager.isEditable else { return }

        guard let jpeg = ImageProcessing.jpeg(from: data) else {
            imageError = String(localized: "This image can't be added.")
            return
        }

        let file: URL
        do {
            file = try ImageFiles.save(jpeg)
        } catch {
            imageError = String(localized: "The image couldn't be saved on this device.")
            return
        }

        guard let stepId = writeopiaManager.addImage(
            path: file.path(percentEncoded: false),
            uploading: imageUploader != nil
        ) else { return }

        guard let imageUploader else { return }
        let url = try? await imageUploader.uploadImage(jpeg, fileName: file.lastPathComponent, mimeType: "image/jpeg")
        writeopiaManager.imageUploadFinished(stepId: stepId, url: url)
    }

    // MARK: - Drawing

    /// Stores a drawing from the drawing editor: a new `DRAWING` step at the end of the document,
    /// or the updated strokes of the step with `stepId`. Empty new drawings are dropped.
    public func saveDrawing(_ drawing: DrawingData, stepId: String?) {
        if let stepId {
            writeopiaManager.updateText(drawing.toJson(), stepId: stepId)
        } else if !drawing.isEmpty {
            writeopiaManager.addAtTheEnd(StoryStep(type: .drawing, text: drawing.toJson(), position: 0))
        }
    }

    // MARK: - Delete

    /// Deletes the document, like "Delete" of the Compose editor. Returns true when it's gone so
    /// the editor can close.
    public func deleteDocument() async -> Bool {
        pushTask?.cancel()
        aiTask?.cancel()
        do {
            try await repository.deleteDocument(id: documentId)
            isDeleted = true
            return true
        } catch {
            deleteError = error.userMessage
            return false
        }
    }

    // MARK: - Menu

    public var isLocked: Bool { !writeopiaManager.isEditable }

    /// Locks the document against edits, like "Lock document" in the Compose menu.
    public func toggleLock() {
        writeopiaManager.isEditable.toggle()
        if isLocked {
            writeopiaManager.clearLineSelection()
        }
        documentChanged()
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
        case .document, .selectedLines: AiCommand.allCases
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
        case .selectedLines:
            input = (writeopiaManager.selectedLinesText, writeopiaManager.selectedPositions.last)
        }

        guard let input, !input.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        streamAnswer(command, prompt: input.text, after: input.position)
    }

    /// Characters of the document sent as context of a section or of list suggestions, so the
    /// request fits in the on-device model.
    static let contextLimit = 4000

    /// Writes the content of the heading with `stepId` right below it, like the AI wand of the
    /// Compose app (`aiSection`).
    public func generateSection(stepId: String) {
        guard aiClient != nil, !isAiRunning,
              let heading = writeopiaManager.headingText(stepId: stepId),
              let position = writeopiaManager.position(of: stepId)
        else { return }

        let document = String(writeopiaManager.documentText.prefix(Self.contextLimit))
        let prompt = """
            Create a document section for a document.
            The document is:
            ```
            \(document)
            ```

            Use the language of the text. Do not add titles. Create content for this section: \(heading)
            """
        streamAnswer(.prompt, prompt: prompt, after: position)
    }

    /// Asks the AI for the next items of the list that ends with `stepId` and shows them as
    /// suggestions, like `generateSuggestionsList` of the SDK. Not streamed: the items show up
    /// together.
    public func suggestListItems(after stepId: String) {
        guard let aiClient, !isAiRunning, writeopiaManager.isEditable else { return }

        let context = String(writeopiaManager.documentText.prefix(Self.contextLimit))
        let prompt = "Generate a list of options. Start each options with a line break and \"-\". Generate at most 5 items. Use this context to generate the list: \(context)"

        suggestionTask?.cancel()
        // Shares `isAiRunning` with the answers, so only one AI request runs at a time.
        isAiRunning = true
        suggestionTask = Task { [weak self] in
            var answer = ""
            do {
                for try await partial in aiClient.stream(.prompt, prompt: prompt) {
                    answer = partial
                }
            } catch {
                self?.isAiRunning = false
                return
            }
            guard let self else { return }
            self.isAiRunning = false
            guard !Task.isCancelled else { return }
            let items = MarkdownSteps.listItems(answer, limit: 5)
            self.writeopiaManager.showAiSuggestions(items, after: stepId)
        }
    }

    public func acceptAiSuggestions() {
        writeopiaManager.acceptAiSuggestions()
    }

    public func dismissAiSuggestions() {
        suggestionTask?.cancel()
        writeopiaManager.dismissAiSuggestions()
    }

    /// Loads the model ahead of a request, e.g. when the AI dialog opens.
    public func prewarmAi() {
        aiClient?.prewarm()
    }

    /// Streams the answer of `prompt` into a new AI answer after `position`. A loading step
    /// shows until the first part of the answer arrives.
    private func streamAnswer(_ command: AiCommand, prompt: String, after position: Double?) {
        guard let aiClient, !isAiRunning else { return }

        let answerId = writeopiaManager.loadingAtPosition(position)
        isAiRunning = true

        aiTask = Task { [weak self] in
            var receivedAnswer = false
            do {
                for try await answer in aiClient.stream(command, prompt: prompt) {
                    receivedAnswer = true
                    self?.writeopiaManager.showAiAnswer(answer, stepId: answerId)
                }
                if !receivedAnswer {
                    self?.writeopiaManager.removeStep(stepId: answerId)
                }
            } catch is CancellationError {
                if !receivedAnswer { self?.writeopiaManager.removeStep(stepId: answerId) }
            } catch let error as AiStreamError {
                self?.writeopiaManager.showAiAnswer(String(localized: "Error. Message: \(error.message)"), stepId: answerId)
            } catch {
                self?.writeopiaManager.showAiAnswer(String(localized: "Error. Message: \(error.userMessage)"), stepId: answerId)
            }
            self?.writeopiaManager.aiAnswerFinished(stepId: answerId)
            self?.isAiRunning = false
        }
    }

    public func cancelAi() {
        aiTask?.cancel()
        suggestionTask?.cancel()
    }

    // MARK: - Persistence and sync

    private func show(_ document: WrDocument) {
        loadedDocument = document
        writeopiaManager.loadDocument(document)
        changeCountAtLoad = writeopiaManager.changeCount
        savedSteps = Dictionary(writeopiaManager.documentContent.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    /// Merges the backend copy into the one shown, like `fetchAndMergeFromBackend`. The merge is
    /// only shown when nothing was typed yet, so no edit is lost; otherwise the local edits win
    /// and are sent with the next sync.
    public func mergeFromBackend() async {
        guard let syncing else { return }
        isSyncing = true
        defer { isSyncing = false }

        do {
            guard let merged = try await syncing.fetchAndMerge(documentId: documentId) else {
                lastSyncFailed = false
                return
            }
            lastSyncFailed = false
            if writeopiaManager.changeCount == changeCountAtLoad {
                show(merged)
                pushedSteps = savedSteps
            }
        } catch {
            lastSyncFailed = true
        }
    }

    /// Called on every change of the document: it's saved on the device right away, and sent to
    /// the backend after a pause in the typing.
    public func documentChanged() {
        guard hasLoaded else { return }

        saveNow()

        guard syncing != nil else { return }
        pushTask?.cancel()
        pushTask = Task { [weak self, pushDelay] in
            try? await Task.sleep(for: pushDelay)
            guard !Task.isCancelled else { return }
            await self?.pushNow()
        }
    }

    /// Sends what's pending right away, e.g. when leaving the editor.
    public func flush() async {
        pushTask?.cancel()
        saveNow()
        await pushNow()
    }

    /// The document as it is in the editor, with the steps changed since the last save stamped
    /// with the current time (so merges pick the newest copy of each step).
    private func documentToSave() -> (document: WrDocument, changed: Bool) {
        let now = Date.nowMillis
        var changed = false
        let steps = writeopiaManager.documentContent.map { step -> StoryStep in
            var step = step
            if let saved = savedSteps[step.id], saved.hasSameContent(as: step) {
                step.lastUpdatedAt = saved.lastUpdatedAt
                if saved.position != step.position { changed = true }
            } else {
                step.lastUpdatedAt = now
                changed = true
            }
            return step
        }
        if Set(steps.map(\.id)) != Set(savedSteps.keys) { changed = true }

        let base = loadedDocument ?? WrDocument(id: documentId, title: "", workspaceId: "")
        let title = writeopiaManager.title
        if title != base.title || isLocked != base.isLocked { changed = true }

        var document = base
        document.title = title
        document.content = steps
        document.isLocked = isLocked
        if changed { document.lastUpdatedAt = now }
        return (document, changed)
    }

    /// Writes the edit to the device. With a `StepStore` (SQLite) only the changed steps are
    /// written, like `OnUpdateDocumentTracker`; other repositories get the whole document.
    func saveNow() {
        guard hasLoaded, !isDeleted else { return }
        let (document, changed) = documentToSave()
        guard changed else { return }

        let changedSteps = document.content.filter { step in
            guard let saved = savedSteps[step.id] else { return true }
            return saved != step
        }
        let currentIds = Set(document.content.map(\.id))
        let deletedIds = savedSteps.keys.filter { !currentIds.contains($0) }

        do {
            if let store = repository as? StepStore {
                try store.saveEdit(document: document, changedSteps: changedSteps, deletedStepIds: deletedIds)
            } else {
                let repository = repository
                Task { try? await repository.save(document) }
            }
            loadedDocument = document
            savedSteps = Dictionary(document.content.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        } catch {
            errorMessage = String(localized: "The document couldn't be saved on this device.")
        }
    }

    /// Sends the steps changed and deleted since the last push, like `OnUpdateStoryStepSyncTracker`.
    func pushNow() async {
        guard hasLoaded, !isDeleted, let syncing, let document = loadedDocument else { return }

        let current = document.content
        let changes = current.filter { step in
            guard let pushed = pushedSteps[step.id] else { return true }
            return !pushed.hasSameContent(as: step) || pushed.position != step.position
        }
        let currentIds = Set(current.map(\.id))
        let deletions = pushedSteps.keys.filter { !currentIds.contains($0) }
        guard !changes.isEmpty || !deletions.isEmpty else { return }

        isSyncing = true
        defer { isSyncing = false }
        do {
            lastSyncTimestamp = try await syncing.pushSteps(
                document: document,
                changes: changes,
                deletions: deletions,
                lastSyncTimestamp: lastSyncTimestamp
            )
            pushedSteps = Dictionary(current.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
            lastSyncFailed = false
        } catch {
            // Kept for the next push, or the next sync of the folder sends the whole document.
            lastSyncFailed = true
        }
    }

    /// Title as it is being typed.
    public var title: String { writeopiaManager.title }

    public func loadDocument() async {
        guard !hasLoaded else { return }

        isLoading = true
        defer { isLoading = false }

        do {
            let document = try await repository.document(id: documentId)
            show(document)
            hasLoaded = true
            errorMessage = nil
            lastSyncTimestamp = document.lastSyncedAt ?? 0
            pushedSteps = savedSteps
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.userMessage
        }
    }
}
