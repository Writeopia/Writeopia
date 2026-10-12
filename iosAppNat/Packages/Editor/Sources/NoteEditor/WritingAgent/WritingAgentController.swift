import Foundation
import Observation
import OSLog
import Writeopia
import WriteopiaUI
import WrData
import WrModels

/// The writing agent of a document, like `WritingAgentController` of the Kotlin app. It watches
/// what the user writes and, each time the number of sentences of the focused paragraph changes,
/// asks the backend which actions the writer wants. When a document opens, it asks once about the
/// whole document (a TL;DR, a conclusion).
///
/// Only confident suggestions come back; nothing is shown otherwise. The suggestions remember the
/// id of the paragraph they are about, so an action finds it again after other blocks move.
///
/// Two offers are decided here, without the backend: a document with text but no title is offered
/// a title, and a document that sits in the root folder is offered a home. Only when the user
/// takes the folder offer does a dialog open and the backend judge the first workspace folders.
/// A document without tags is offered tags the same way: the dialog shows the catalogue tags the
/// backend says fit, selected, and a new tag made up by the AI.
@Observable
public final class WritingAgentController {
    public private(set) var state = WritingSuggestionsState.empty
    /// True when the user folded the box down to a small "AI suggestions" badge with a count, to
    /// focus on the text. The suggestions keep coming; only the list is hidden.
    public private(set) var collapsed = false

    public let executor: WritingActionExecutor
    @ObservationIgnored private let api: WritingAgentServing
    @ObservationIgnored private let manager: WriteopiaStateManager
    @ObservationIgnored private let folders: DocumentFolders?
    @ObservationIgnored private let tags: DocumentTags?
    @ObservationIgnored private let ui: () -> WritingAgentUI
    @ObservationIgnored private let isEnabled: () -> Bool
    @ObservationIgnored private let debounce: Duration
    @ObservationIgnored private let minimumDocumentLength: Int
    @ObservationIgnored private let folderMinimumDocumentLength: Int
    @ObservationIgnored private let tagMinimumDocumentLength: Int
    @ObservationIgnored private let untitledDocumentMinimumSentences: Int

    /// What the backend answered last. `state` is this plus what is decided locally.
    @ObservationIgnored private var fetched = WritingSuggestionsState.empty
    @ObservationIgnored private var offerDocumentTitle = false
    @ObservationIgnored private var documentTitleDeclinedFor: String?
    @ObservationIgnored private var offerFolder = false
    @ObservationIgnored private var folderDialog: FolderDialogState?
    @ObservationIgnored private var foldersDeclinedFor: String?
    @ObservationIgnored private var foldersCheckedFor: String?
    @ObservationIgnored private var offerTags = false
    @ObservationIgnored private var tagDialog: TagDialogState?
    @ObservationIgnored private var tagsDeclinedFor: String?
    @ObservationIgnored private var tagsCheckedFor: String?
    @ObservationIgnored private var tagRequest: Task<Void, Never>?
    @ObservationIgnored private var sentenceEnds: [String: Int] = [:]
    @ObservationIgnored private var openedDocumentId: String?
    @ObservationIgnored private var request: Task<Void, Never>?
    @ObservationIgnored private var folderRequest: Task<Void, Never>?
    @ObservationIgnored private var executing: Task<Void, Never>?

    static let logger = WritingActionExecutor.logger
    public static let documentTextLimit = 6_000
    public static let minimumDocumentLength = 200
    public static let folderMinimumDocumentLength = 40
    public static let tagMinimumDocumentLength = 40

    public init(
        api: WritingAgentServing,
        manager: WriteopiaStateManager,
        executor: WritingActionExecutor,
        folders: DocumentFolders? = nil,
        tags: DocumentTags? = nil,
        ui: @escaping () -> WritingAgentUI = { NoWritingAgentUI() },
        isEnabled: @escaping () -> Bool = { true },
        debounce: Duration = .milliseconds(600),
        minimumDocumentLength: Int = WritingAgentController.minimumDocumentLength,
        folderMinimumDocumentLength: Int = WritingAgentController.folderMinimumDocumentLength,
        tagMinimumDocumentLength: Int = WritingAgentController.tagMinimumDocumentLength,
        untitledDocumentMinimumSentences: Int = 1
    ) {
        self.api = api
        self.manager = manager
        self.executor = executor
        self.folders = folders
        self.tags = tags
        self.tagMinimumDocumentLength = tagMinimumDocumentLength
        self.ui = ui
        self.isEnabled = isEnabled
        self.debounce = debounce
        self.minimumDocumentLength = minimumDocumentLength
        self.folderMinimumDocumentLength = folderMinimumDocumentLength
        self.untitledDocumentMinimumSentences = untitledDocumentMinimumSentences
    }

    public func stop() {
        request?.cancel()
        folderRequest?.cancel()
        tagRequest?.cancel()
        executor.cancel()
    }

    // MARK: - What the user does

    public func toggleCollapsed() {
        collapsed.toggle()
    }

    /// Hides the suggestions, the folder offer included. They come back with the next sentence.
    public func dismiss() {
        request?.cancel()
        folderRequest?.cancel()
        tagRequest?.cancel()
        declineDocumentTitle()
        declineFolder()
        declineTags()
        fetched = .empty
        publish()
    }

    /// Applies `action` to the paragraph the suggestions are about and drops it from the box.
    public func execute(_ action: WritingSuggestionAction) {
        let current = state
        guard current.suggestions.contains(where: { $0.action == action }) else { return }

        if action == .documentTitle {
            declineDocumentTitle()
        } else {
            fetched.suggestions.removeAll { $0.action == action }
        }
        publish()

        let stepId = current.stepId
        let ui = ui()
        executing = Task { [executor] in
            await executor.execute(action, stepId: stepId, ui: ui)
        }
    }

    /// Takes the folder offer: opens the dialog with a spinner and asks the backend which of the
    /// first workspace folders the document belongs to.
    public func openFolderDialog() {
        guard let folders, let documentId = openedDocumentId, offerFolder, folderDialog == nil else { return }

        folderDialog = FolderDialogState(isLoading: true)
        publish()

        folderRequest?.cancel()
        let text = documentText()
        folderRequest = Task { await askFolders(folders, documentId: documentId, text: text) }
    }

    /// Closes the folder dialog without moving the document. The box keeps offering it.
    public func dismissFolderDialog() {
        folderRequest?.cancel()
        folderDialog = nil
        publish()
    }

    /// Takes the tag offer: opens the dialog with a spinner and asks the backend which tags of
    /// the catalogue the document should carry.
    public func openTagDialog() {
        guard let tags, let documentId = openedDocumentId, offerTags, tagDialog == nil else { return }

        tagDialog = TagDialogState(isLoading: true)
        publish()

        tagRequest?.cancel()
        let text = documentText()
        tagRequest = Task { await askTags(tags, documentId: documentId, text: text) }
    }

    /// Closes the tag dialog without tagging the document. The box keeps offering it.
    public func dismissTagDialog() {
        tagRequest?.cancel()
        tagDialog = nil
        publish()
    }

    /// Keeps or drops one of the offered tags before they are added.
    public func toggleTag(_ tagId: String) {
        guard var dialog = tagDialog, !dialog.isLoading, !dialog.isCreating,
              dialog.options.contains(where: { $0.tagId == tagId })
        else { return }

        if dialog.selected.contains(tagId) {
            dialog.selected.remove(tagId)
        } else {
            dialog.selected.insert(tagId)
        }
        tagDialog = dialog
        publish()
    }

    /// Adds the selected tags to the document, closes the dialog and withdraws the offer.
    public func applyTags() {
        guard let tags, let dialog = tagDialog, !dialog.isLoading, !dialog.isCreating else { return }

        let selected = dialog.options.map(\.tagId).filter { dialog.selected.contains($0) }
        declineTags()
        publish()
        guard !selected.isEmpty else { return }

        Self.logger.info("adding tags \(selected.joined(separator: ", "), privacy: .public) to the document")
        executing = Task { [weak self] in
            do {
                try await tags.addTags(selected)
            } catch {
                self?.executor.error = WritingActionExecutor.message(of: error)
            }
        }
    }

    /// Makes a tag made up by the AI and adds it to the document. The dialog shows a spinner
    /// meanwhile and closes once the tag was added, or when the AI failed.
    public func createTagWithAi() {
        guard let tags, var dialog = tagDialog, !dialog.isLoading, !dialog.isCreating else { return }

        dialog.isCreating = true
        tagDialog = dialog
        publish()

        let title = manager.title
        let text = documentText()
        executing = Task { [weak self] in
            do {
                let tag = try await tags.createTagWithAi(documentTitle: title, documentText: text)
                Self.logger.info("document tagged with the new tag \"\(tag.name, privacy: .public)\"")
            } catch {
                self?.executor.error = WritingActionExecutor.message(of: error)
            }
            self?.declineTags()
            self?.publish()
        }
    }

    /// Moves the document to `folderId`, one of the folders the dialog offers, and closes it.
    public func moveToFolder(_ folderId: String) {
        guard let folders, let dialog = folderDialog, !dialog.isCreating,
              dialog.options.contains(where: { $0.folderId == folderId })
        else { return }

        declineFolder()
        publish()

        executing = Task { [weak self] in
            do {
                try await folders.moveTo(folderId: folderId)
            } catch {
                self?.executor.error = WritingActionExecutor.message(of: error)
            }
        }
    }

    /// Makes a folder named by the AI and moves the document there. The dialog shows a spinner
    /// meanwhile and closes once the document moved, or when the AI failed.
    public func createFolder() {
        guard let folders, var dialog = folderDialog, !dialog.isLoading, !dialog.isCreating else { return }

        dialog.isCreating = true
        folderDialog = dialog
        publish()

        let title = manager.title
        let text = documentText()
        executing = Task { [weak self] in
            do {
                let name = try await folders.createFolderAndMove(documentTitle: title, documentText: text)
                Self.logger.info("document moved to the new folder \"\(name, privacy: .public)\"")
            } catch {
                self?.executor.error = WritingActionExecutor.message(of: error)
            }
            self?.declineFolder()
            self?.publish()
        }
    }

    // MARK: - What the document does

    /// A document was shown in the editor: forgets the previous one and asks about this one.
    public func documentOpened(id: String) {
        guard !id.isEmpty, id != openedDocumentId else { return }

        openedDocumentId = id
        request?.cancel()
        folderRequest?.cancel()
        tagRequest?.cancel()
        fetched = .empty
        offerFolder = false
        folderDialog = nil
        offerTags = false
        tagDialog = nil
        refreshDocumentTitleOffer()
        publish()

        let text = documentText()
        considerFolders(documentId: id, text: text)
        considerTags(documentId: id, text: text)

        guard text.count >= minimumDocumentLength else {
            Self.logger.info("document \(id, privacy: .public) opened with \(text.count) chars, under the minimum: not asking")
            return
        }

        Self.logger.info("document \(id, privacy: .public) opened with \(text.count) chars: asking about the whole document")
        let anchor = manager.currentStory.sortedStories.first(where: \.isTitle) ?? manager.currentStory.sortedStories.first
        let title = manager.title
        request = Task {
            await ask(
                stepId: anchor?.id,
                request: WritingSuggestionsRequest(scope: .documentOpened, documentTitle: title, documentText: text)
            )
        }
    }

    /// Called on every change of the document. The first sight of a paragraph only records its
    /// sentences; a change asks, after a pause.
    public func storyChanged() {
        refreshDocumentTitleOffer()
        if let id = openedDocumentId {
            let text = documentText()
            considerFolders(documentId: id, text: text)
            considerTags(documentId: id, text: text)
        }

        guard let step = manager.focusedStep, !step.isTitle, let text = step.text else { return }

        let count = SentenceEnds.count(text)
        let previous = sentenceEnds[step.id]
        sentenceEnds[step.id] = count

        if let shownId = fetched.stepId, manager.step(withId: shownId) == nil {
            fetched = .empty
            publish()
        }

        guard let previous, previous != count, !text.trimmingCharacters(in: .whitespaces).isEmpty else { return }

        Self.logger.info("paragraph \(step.id, privacy: .public) went from \(previous) to \(count) sentence ends: asking after a pause")
        scheduleAsk(stepId: step.id)
    }

    // MARK: - Internals

    private func declineDocumentTitle() {
        offerDocumentTitle = false
        documentTitleDeclinedFor = openedDocumentId
    }

    private func declineFolder() {
        offerFolder = false
        folderDialog = nil
        foldersDeclinedFor = openedDocumentId
    }

    private func declineTags() {
        offerTags = false
        tagDialog = nil
        tagsDeclinedFor = openedDocumentId
    }

    private func publish() {
        var shown = fetched
        if offerDocumentTitle {
            shown.suggestions.insert(WritingSuggestion(action: .documentTitle, probability: 1), at: 0)
        }
        shown.offerFolder = offerFolder
        shown.folderDialog = folderDialog
        shown.offerTags = offerTags
        shown.tagDialog = tagDialog
        state = shown
    }

    /// Offers a title once the document has a finished sentence and its title is empty.
    private func refreshDocumentTitleOffer() {
        let hasTitle = !manager.title.trimmingCharacters(in: .whitespaces).isEmpty
        let declined = documentTitleDeclinedFor != nil && documentTitleDeclinedFor == openedDocumentId
        let body = manager.currentStory.sortedStories
            .filter { !$0.isTitle }
            .compactMap(\.text)
            .joined(separator: "\n")
        let enoughSentences = SentenceEnds.count(body) >= untitledDocumentMinimumSentences

        let offer = !hasTitle && !declined && enoughSentences
        if offer != offerDocumentTitle {
            offerDocumentTitle = offer
            if offer {
                Self.logger.info("the document has no title: offering one")
            } else {
                Self.logger.info("document title offer withdrawn")
            }
            publish()
        }
    }

    /// Offers a folder once per document, as soon as it has enough text, when it sits in the root.
    private func considerFolders(documentId: String, text: String) {
        guard let folders, foldersCheckedFor != documentId, foldersDeclinedFor != documentId,
              text.count >= folderMinimumDocumentLength
        else { return }

        foldersCheckedFor = documentId
        let currentFolder = folders.currentFolderId()
        guard currentFolder == Folder.rootId else {
            Self.logger.info("document \(documentId, privacy: .public) is in folder \(currentFolder, privacy: .public), not the root: no folder offered")
            return
        }
        Self.logger.info("document \(documentId, privacy: .public) is in the root: offering a folder")
        offerFolder = true
        publish()
    }

    /// Offers tags once per document, as soon as it has enough text and carries none: when it
    /// opens, or later while the user writes. The backend is not asked until the user takes the
    /// offer. The offer is withdrawn when the document gets a tag another way.
    private func considerTags(documentId: String, text: String) {
        guard let tags else { return }

        if offerTags, !tags.currentTagIds().isEmpty {
            Self.logger.info("document \(documentId, privacy: .public) got tags: tag offer withdrawn")
            offerTags = false
            tagDialog = nil
            publish()
            return
        }

        guard tagsCheckedFor != documentId, tagsDeclinedFor != documentId, text.count >= tagMinimumDocumentLength else { return }
        tagsCheckedFor = documentId
        guard tags.currentTagIds().isEmpty else { return }

        Self.logger.info("document \(documentId, privacy: .public) has no tags: offering some")
        offerTags = true
        publish()
    }

    /// Fills the open dialog: the tags the backend says the document should carry, among the first
    /// tags of the catalogue, all selected. Without tags, offline, or when the backend fails, the
    /// dialog only offers a new tag.
    private func askTags(_ tags: DocumentTags, documentId: String, text: String) async {
        var matches: [TagSuggestion] = []
        var catalogueIsEmpty = false
        do {
            let candidates = try await tags.candidates(limit: TagSuggestionsRequest.maxTags)
            if candidates.isEmpty {
                catalogueIsEmpty = true
                Self.logger.info("the workspace has no tags: the dialog offers only a new tag")
            } else if !isEnabled() {
                Self.logger.info("tag request skipped: the agent is disabled")
            } else {
                Self.logger.info("asking about \(candidates.count) tags: \(candidates.map(\.name).joined(separator: ", "), privacy: .public)")
                let response = try await api.tagSuggestions(
                    TagSuggestionsRequest(documentTitle: manager.title, documentText: text, tags: candidates)
                )
                matches = response.suggestions.compactMap { dto in
                    candidates.first { $0.id == dto.tagId }.map {
                        TagSuggestion(tagId: $0.id, name: $0.name, description: $0.description, probability: dto.probability)
                    }
                }
                Self.logger.info("tag answer: \(matches.map { "\($0.name) \(Int($0.probability * 100))%" }.joined(separator: ", "), privacy: .public)")
            }
        } catch is CancellationError {
            return
        } catch {
            Self.logger.error("tag request failed: \(error.localizedDescription, privacy: .public); the dialog offers only a new tag")
        }

        guard openedDocumentId == documentId, tagDialog != nil else { return }
        tagDialog = TagDialogState(
            isLoading: false,
            options: matches,
            selected: Set(matches.map(\.tagId)),
            catalogueIsEmpty: catalogueIsEmpty
        )
        publish()
    }

    /// Fills the open dialog: the folders the backend says the document belongs to. Without
    /// folders, offline, or when the backend fails, the dialog only offers a new folder.
    private func askFolders(_ folders: DocumentFolders, documentId: String, text: String) async {
        var matches: [FolderSuggestion] = []
        do {
            let candidates = try await folders.candidates(limit: FolderSuggestionsRequest.maxFolders)
            if candidates.isEmpty {
                Self.logger.info("no folder to go to: the dialog offers only a new folder")
            } else if !isEnabled() {
                Self.logger.info("folder request skipped: the agent is disabled")
            } else {
                Self.logger.info("asking about \(candidates.count) folders: \(candidates.map(\.name).joined(separator: ", "), privacy: .public)")
                let response = try await api.folderSuggestions(
                    FolderSuggestionsRequest(documentTitle: manager.title, documentText: text, folders: candidates)
                )
                matches = response.suggestions.compactMap { dto in
                    candidates.first { $0.id == dto.folderId }.map { FolderSuggestion(folderId: $0.id, name: $0.name, probability: dto.probability) }
                }
                Self.logger.info("folder answer: \(matches.map { "\($0.name) \(Int($0.probability * 100))%" }.joined(separator: ", "), privacy: .public)")
            }
        } catch is CancellationError {
            return
        } catch {
            Self.logger.error("folder request failed: \(error.localizedDescription, privacy: .public); the dialog offers only a new folder")
        }

        guard openedDocumentId == documentId, folderDialog != nil else { return }
        folderDialog = FolderDialogState(isLoading: false, options: matches)
        publish()
    }

    private func scheduleAsk(stepId: String) {
        request?.cancel()
        request = Task { [debounce] in
            try? await Task.sleep(for: debounce)
            guard !Task.isCancelled else { return }

            guard let step = manager.step(withId: stepId), let text = step.text, !text.trimmingCharacters(in: .whitespaces).isEmpty else {
                Self.logger.info("paragraph \(stepId, privacy: .public) is gone or blank: not asking")
                return
            }
            await ask(
                stepId: stepId,
                request: WritingSuggestionsRequest(
                    scope: .writing,
                    text: text,
                    blockType: Self.blockTypeName(step.type),
                    documentTitle: manager.title,
                    documentText: documentText()
                )
            )
        }
    }

    /// The name of the step type as the Kotlin `StoryTypes` enum spells it.
    static func blockTypeName(_ type: StoryType) -> String {
        switch type.number {
        case StoryType.checkItem.number: "CHECK_ITEM"
        case StoryType.unorderedListItem.number: "UNORDERED_LIST_ITEM"
        case StoryType.codeBlock.number: "CODE_BLOCK"
        case StoryType.aiAnswer.number: "AI_ANSWER"
        case StoryType.title.number: "TITLE"
        default: "TEXT"
        }
    }

    private func ask(stepId: String?, request: WritingSuggestionsRequest) async {
        guard isEnabled() else {
            Self.logger.info("\(request.scope.rawValue, privacy: .public) request skipped: the agent is disabled (locked)")
            return
        }

        fetched.isLoading = true
        publish()
        let started = ContinuousClock.now

        do {
            let response = try await api.suggestions(request)
            let suggestions = response.suggestions.compactMap { dto -> WritingSuggestion? in
                guard let action = dto.action, action != .spreadsheet else { return nil }
                return WritingSuggestion(action: action, probability: dto.probability)
            }
            let elapsed = started.duration(to: .now)
            Self.logger.info("\(request.scope.rawValue, privacy: .public) answer in \(elapsed.formatted(), privacy: .public): \(suggestions.map { "\($0.action.rawValue) \(Int($0.probability * 100))%" }.joined(separator: ", "), privacy: .public)")
            guard !Task.isCancelled else { return }
            fetched = WritingSuggestionsState(stepId: stepId, scope: request.scope, suggestions: suggestions, isLoading: false)
        } catch is CancellationError {
            return
        } catch {
            Self.logger.error("\(request.scope.rawValue, privacy: .public) request failed: \(error.localizedDescription, privacy: .public)")
            fetched.isLoading = false
        }
        publish()
    }

    private func documentText() -> String {
        String(manager.documentTextWithTitle.prefix(Self.documentTextLimit))
    }
}
