import Foundation
import Writeopia
import WriteopiaUI
import WrModels
import WrSession

/// What Claude Code can do with the documents: create one and add to one, in the editor on
/// screen, so the person watches it happen. One call at a time: a second one waits for the
/// first. Mirrors `LiveDocuments.kt` of the Compose app.
public final class LiveDocuments: LiveDocumentsWriting {
    private let session: () -> AppSession?
    private let editors: LiveEditors
    private let writer: LiveWriter
    private let openTimeout: Duration
    private var queue: Task<Void, Never>?
    /// The document as Claude Code last saw it (at the end of its last call), to tell what changed since.
    private var lastSeen: DocumentSnapshot?

    public init(
        session: @escaping () -> AppSession?,
        editors: LiveEditors = .shared,
        writer: LiveWriter = LiveWriter(),
        openTimeout: Duration = .seconds(15)
    ) {
        self.session = session
        self.editors = editors
        self.writer = writer
        self.openTimeout = openTimeout
    }

    public func create(title: String, markdown: String?, folderId: String?) async -> LiveOutcome {
        await inAiMode { [self] ticket in
            guard let session = readySession() else { return Self.noWorkspace }

            let folder = folderId?.trimmingCharacters(in: .whitespaces).nonEmpty ?? currentFolderId()
            guard await folderExists(folder, in: session) else { return Self.noFolder(folder) }

            let document: WrDocument
            do {
                // Empty: the title is typed in the editor, like a document made from the menu.
                document = try await session.documents.createDocument(title: "", parentId: folder)
            } catch {
                return .failed("Writeopia could not create the document: \(error.localizedDescription)")
            }

            guard let editor = await open(documentId: document.id, title: document.displayTitle) else {
                return .failed("Writeopia created the document but could not open it.")
            }
            return await write(
                into: editor,
                title: title.trimmingCharacters(in: .whitespacesAndNewlines),
                markdown: markdown ?? "",
                ticket: ticket
            )
        }
    }

    public func append(documentId: String?, markdown: String) async -> LiveOutcome {
        // In a working session when the call came: checked once its turn comes, just before writing.
        let inSession = editors.mode.isOn
        return await inAiMode { [self] ticket in
            if inSession, let switched = switchedDocument(documentId) { return switched }
            switch await openEditor(documentId) {
            case let .success(editor): return await write(into: editor, title: nil, markdown: markdown, ticket: ticket)
            case let .failure(failure): return failure.outcome
            }
        }
    }

    /// Without a document_id, in the middle of a working session (AI mode on), the open document
    /// must still be the one Claude was writing in: when the person moved to another one, Claude is
    /// told where they are now, to decide again, instead of writing there unasked.
    private func switchedDocument(_ documentId: String?) -> LiveOutcome? {
        guard documentId?.trimmingCharacters(in: .whitespaces).nonEmpty == nil,
              let last = lastSeen, let current = editors.current, current.documentId != last.documentId else {
            return nil
        }
        return .failed(
            "The person switched documents: Writeopia shows \"\(current.manager.title)\" (document_id: \(current.documentId)) " +
                "now, not \"\(last.title)\" (document_id: \(last.documentId)) where you were writing. Call " +
                "get_current_place, then pass the document_id of the document to write in."
        )
    }

    /// Where the person is: the document open in the editor (with its content as Markdown when
    /// `includeContent`) or the folder open, and what changed since Claude's last call. Only
    /// reads: no AI mode.
    public func currentPlace(includeContent: Bool) async -> LiveOutcome {
        guard let session = readySession() else { return Self.noWorkspace }

        // The repositories first: after them nothing suspends, so the report, its content and the
        // baseline kept for the next one are the same state of the editor.
        let documentId = editors.current?.documentId
        var path: [LiveFolder] = []
        if let documentId {
            let parentId = (try? await session.documents.document(id: documentId))?.parentId ?? Folder.rootId
            path = ((try? await session.documents.folderPath(to: parentId)) ?? [])
                .map { LiveFolder(id: $0.id, name: $0.title, parentId: $0.parentId) }
        }
        let catalogue = (try? await session.documents.tags()) ?? []
        let folderId = currentFolderId()
        let currentFolder: LiveFolder? = if folderId == Folder.rootId {
            nil
        } else {
            (try? await session.documents.folder(id: folderId)).flatMap { $0 }
                .map { LiveFolder(id: $0.id, name: $0.title, parentId: $0.parentId) }
        }

        // The person may have moved meanwhile: the folder path belongs to the document read above.
        let editor = editors.current.flatMap { $0.documentId == documentId ? $0 : nil }
        let now = editor.map(snapshot)

        var document: OpenDocument?
        if let editor {
            let manager = editor.manager
            let tagIds = Set(editor.tagger?.documentTagIds ?? [])
            let tagNames = catalogue
                .filter { tagIds.contains($0.id) && !$0.deleted }
                .map(\.name)
                .sorted { $0.lowercased() < $1.lowercased() }
            let title = manager.currentStory.sortedStories.first(where: \.isTitle)
            document = OpenDocument(
                id: editor.documentId,
                title: manager.title,
                folderPath: path,
                tags: tagNames,
                isLocked: !manager.isEditable,
                hasHeaderPicture: !(title?.url ?? "").isEmpty
            )
        }

        let change = lastSeen?.change(to: now)
        let content = includeContent ? editor.map { DocumentToMarkdown.parse($0.manager.documentContent) } : nil

        // Claude has seen it now: the next report tells what changed from here.
        if let now { lastSeen = now }
        return .place(PlaceReport.describe(document: document, currentFolder: currentFolder, change: change, content: content))
    }

    private func snapshot(_ editor: LiveEditors.Editor) -> DocumentSnapshot {
        let manager = editor.manager
        let blocks = Dictionary(
            manager.documentContent.map { step in
                // Sorted, so the same block always reads the same; position for the moves.
                let tags = step.tags.map { "\($0.tag):\($0.position)" }.sorted()
                let spans = step.spans.map { "\($0.start):\($0.end):\($0.span):\($0.extra ?? "")" }.sorted()
                let fields = [step.position, "\(step.type.number)", step.text ?? "", String(describing: step.checked), step.url ?? ""]
                return (step.id, (fields + ["\(tags)", "\(spans)"]).joined(separator: "|"))
            },
            uniquingKeysWith: { first, _ in first }
        )
        return DocumentSnapshot(documentId: editor.documentId, title: manager.title, blocks: blocks)
    }

    /// Draws a header picture for the document with the cloud AI of Writeopia, from its title and
    /// text and the `description` (what the picture should show, optional), and puts it in the
    /// header. Refused when the header already has a picture: Claude only adds.
    public func setHeaderImage(documentId: String?, description: String?) async -> LiveOutcome {
        await inAiMode { [self] ticket in
            let editor: LiveEditors.Editor
            switch await openEditor(documentId) {
            case let .success(opened): editor = opened
            case let .failure(failure): return failure.outcome
            }
            let manager = editor.manager
            guard manager.isEditable else {
                return .failed("The document is locked in Writeopia. Unlock it to write in it.")
            }
            guard let title = manager.currentStory.sortedStories.first(where: \.isTitle) else {
                return .failed("The document has no header.")
            }
            if let url = title.url, !url.isEmpty {
                return .failed("The document already has a header picture. Only the person can change it, in the app.")
            }
            guard let pictures = manager.headerImages else {
                return .failed(
                    "Header pictures are drawn by the Writeopia cloud: sign in to an online workspace with cloud AI to use them."
                )
            }

            let url: String
            do {
                url = try await pictures.drawAndKeep(
                    documentTitle: manager.title,
                    documentText: String(manager.documentText.prefix(Self.documentTextLimit)),
                    instructions: description?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                )
            } catch {
                return .failed("The cloud AI drew no picture: \(error.localizedDescription)")
            }
            if editors.isStopped(ticket) { return .stopped }

            manager.setHeaderImage(url)
            lastSeen = snapshot(editor)
            return .headerImageSet(documentId: editor.documentId, title: manager.title, url: url)
        }
    }

    /// The tag catalogue, to reuse tags before making new ones. Only reads: no AI mode.
    /// Finds documents by their title and their words, like the global search: this device and
    /// the backend at once (`searchAll`). Only reads: no AI mode, no banner.
    public func search(query: String, limit: Int) async -> LiveOutcome {
        guard !SearchReport.tokens(query).isEmpty else {
            return .failed("The query has no words to look for: pass one or more words.")
        }
        guard let session = readySession() else {
            return .failed("Writeopia could not search. Check that the app shows a workspace, not a sign-in screen.")
        }

        let found: [WrDocument]
        do {
            found = try await session.documents.searchAll(query: query).documents
        } catch {
            return .failed("Writeopia could not search: \(error.localizedDescription)")
        }

        // The passage: the first prose step holding the words, like the snippets of the search screen.
        let terms = SearchTerms(query)
        let matches = found.map { document in
            SearchMatch(
                documentId: document.id,
                title: document.title,
                parentId: document.parentId,
                text: document.bodySteps.first { step in
                    Self.proseTypes.contains(step.type.number) && step.text.map(terms.matches) == true
                }?.text
            )
        }
        let folders = ((try? await session.documents.allContents().folders) ?? [])
            .filter { !$0.deleted }
            .map { LiveFolder(id: $0.id, name: $0.title, parentId: $0.parentId) }
        return .found(SearchReport.describe(query: query, matches: matches, folders: folders, limit: limit))
    }

    /// The whole document as Markdown, from `offset` on, without opening it. The document open in
    /// the editor is read from the editor, with what the person typed and isn't saved yet; one
    /// not on this device comes from the backend. Only reads: no AI mode, no banner.
    public func read(documentId: String, offset: Int) async -> LiveOutcome {
        let id = documentId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let session = readySession() else {
            return .failed("Writeopia could not read the document. Check that the app shows a workspace, not a sign-in screen.")
        }
        guard let stored = try? await session.documents.document(id: id), !stored.deleted else {
            return .failed(
                "Writeopia has no document with the id \(id). Use a document_id from search_documents, " +
                    "create_document or get_current_place."
            )
        }

        let steps = editors.current.flatMap { $0.documentId == id ? $0.manager.documentContent : nil } ?? stored.content
        let document = ReadDocument(
            id: id,
            title: stored.title,
            parentId: stored.parentId,
            markdown: DocumentToMarkdown.parse(steps)
        )
        let folders = ((try? await session.documents.allContents().folders) ?? [])
            .filter { !$0.deleted }
            .map { LiveFolder(id: $0.id, name: $0.title, parentId: $0.parentId) }
        return .read(ReadReport.describe(document, folders: folders, offset: offset))
    }

    /// Message, check item, list item, code block: the steps the searches look into.
    private static let proseTypes: Set<Int> = [
        StoryType.text.number, StoryType.checkItem.number, StoryType.unorderedListItem.number, StoryType.codeBlock.number,
    ]

    public func listTags() async -> LiveOutcome {
        guard let session = readySession() else { return Self.noWorkspace }
        do {
            let tags = try await session.documents.tags()
                .filter { !$0.deleted }
                .map { LiveTag(id: $0.id, name: $0.name, description: $0.description) }
                .sorted { $0.name.lowercased() < $1.name.lowercased() }
            return .tags(tags)
        } catch {
            return .failed("Writeopia could not read the tags: \(error.localizedDescription)")
        }
    }

    /// Tags the document with `names`: a tag of the catalogue with the same name (any
    /// capitalization) is reused, a new one is made in the catalogue. Tags the document already has
    /// stay; none is removed.
    public func addTags(documentId: String?, names: [String]) async -> LiveOutcome {
        await inAiMode { [self] ticket in
            var seen = Set<String>()
            let wanted = names.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty && seen.insert($0.lowercased()).inserted }
            guard !wanted.isEmpty else { return .failed("Give at least one tag name.") }
            guard wanted.count <= Self.maxTags else { return .failed("Add at most \(Self.maxTags) tags at a time.") }
            guard let session = readySession() else { return Self.noWorkspace }

            let editor: LiveEditors.Editor
            switch await openEditor(documentId) {
            case let .success(opened): editor = opened
            case let .failure(failure): return failure.outcome
            }
            guard editor.manager.isEditable else {
                return .failed("The document is locked in Writeopia. Unlock it to write in it.")
            }
            guard let tagger = editor.tagger else { return .failed("This Writeopia app has no tags.") }

            var catalogue: [DocumentTag]
            do {
                catalogue = try await session.documents.tags().filter { !$0.deleted }
            } catch {
                // Read as empty, every tag would be made again.
                return .failed("Writeopia could not read the tags: \(error.localizedDescription)")
            }
            var added: [(String, Bool)] = []
            var already: [String] = []
            for name in wanted {
                if editors.isStopped(ticket) { return .stopped }
                if let tag = catalogue.first(where: { $0.name.lowercased() == name.lowercased() }) {
                    if tagger.documentTagIds.contains(tag.id) {
                        already.append(tag.name)
                    } else {
                        tagger.addTag(id: tag.id)
                        added.append((tag.name, false))
                    }
                } else {
                    do {
                        let tag = try await tagger.createTag(name: name, description: "")
                        catalogue.append(tag)
                        added.append((tag.name, true))
                    } catch {
                        return .failed("Writeopia could not make the tag \(name): \(error.localizedDescription)")
                    }
                }
            }
            lastSeen = snapshot(editor)
            return .tagged(
                documentId: editor.documentId,
                title: editor.manager.title,
                added: added.map { LiveTagged(name: $0.0, created: $0.1) },
                already: already
            )
        }
    }

    private static let maxTags = 10

    /// As much of the document as the header picture is drawn from, like the Compose app.
    private static let documentTextLimit = 6000

    private struct OpenFailure: Error {
        let outcome: LiveOutcome
    }

    /// The editor of `documentId`, opened when it isn't on screen; the one on screen when nil.
    private func openEditor(_ documentId: String?) async -> Result<LiveEditors.Editor, OpenFailure> {
        guard let id = documentId?.trimmingCharacters(in: .whitespaces), !id.isEmpty else {
            guard let current = editors.current else {
                return .failure(OpenFailure(outcome: .failed(
                    "No document is open in Writeopia. Pass the document_id that create_document returned, or create a document."
                )))
            }
            return .success(current)
        }

        if let current = editors.current, current.documentId == id { return .success(current) }

        guard let session = readySession() else { return .failure(OpenFailure(outcome: Self.noWorkspace)) }
        guard let document = try? await session.documents.document(id: id), !document.deleted,
              let editor = await open(documentId: id, title: document.displayTitle) else {
            return .failure(OpenFailure(outcome: .failed("Writeopia could not open a document with the id \(id).")))
        }
        return .success(editor)
    }

    /// Makes a folder named `name` inside `parentId` (the folder the person has open when nil)
    /// and shows it, so the person sees it appear.
    public func createFolder(name: String, parentId: String?) async -> LiveOutcome {
        await inAiMode { [self] _ in
            guard let session = readySession() else { return Self.noWorkspace }

            let parent = parentId?.trimmingCharacters(in: .whitespaces).nonEmpty ?? currentFolderId()
            guard await folderExists(parent, in: session) else { return Self.noFolder(parent) }

            let title = name.trimmingCharacters(in: .whitespacesAndNewlines)
            let folder: Folder
            do {
                folder = try await session.documents.createFolder(title: title, parentId: parent)
            } catch {
                return .failed("Writeopia could not create the folder: \(error.localizedDescription)")
            }
            ClaudeCode.openFolder?(folder)
            return .folderCreated(folderId: folder.id, name: title, parentId: parent)
        }
    }

    /// Claude Code is done: AI mode ends and the person can edit again. Waits for a call still
    /// writing, so it never ends AI mode in the middle of one.
    public func finish() async -> LiveOutcome {
        let previous = queue
        let editors = editors
        let task = Task { () -> LiveOutcome in
            await previous?.value
            editors.finish()
            return .finished
        }
        // The calls after it wait until AI mode is off.
        queue = Task { _ = await task.value }
        return await task.value
    }

    /// The folders, to choose where to write. Only reads: no AI mode, no banner.
    public func listFolders() async -> LiveOutcome {
        guard let session = readySession() else { return Self.noWorkspace }
        do {
            let folders = try await session.documents.allContents().folders
                .filter { !$0.deleted }
                .map { LiveFolder(id: $0.id, name: $0.title, parentId: $0.parentId) }
            return .folders(folders, currentFolderId: currentFolderId())
        } catch {
            return .failed("Writeopia could not read the folders: \(error.localizedDescription)")
        }
    }

    private func currentFolderId() -> String { ClaudeCode.currentFolderId?() ?? Folder.rootId }

    private func folderExists(_ id: String, in session: AppSession) async -> Bool {
        if id == Folder.rootId { return true }
        guard let folder = try? await session.documents.folder(id: id) else { return false }
        return !folder.deleted
    }

    private static func noFolder(_ id: String) -> LiveOutcome {
        .failed("Writeopia has no folder with the id \(id). Use the folder_id that create_folder returned, or leave it out.")
    }

    private static let noWorkspace = LiveOutcome.failed(
        "Writeopia could not open a new document. Check that the app shows a workspace, not a sign-in screen."
    )

    private func readySession() -> AppSession? {
        guard let session = session(), case .ready = session.phase else { return nil }
        return session
    }

    private func open(documentId: String, title: String) async -> LiveEditors.Editor? {
        guard let openDocument = editors.openDocument else { return nil }
        openDocument(documentId, title)
        return await editors.awaitEditor(documentId: documentId, timeout: openTimeout)
    }

    private func write(into editor: LiveEditors.Editor, title: String?, markdown: String, ticket: Int) async -> LiveOutcome {
        if editors.isStopped(ticket) { return .stopped }
        let manager = editor.manager
        guard manager.isEditable else {
            return .failed("The document is locked in Writeopia. Unlock it to write in it.")
        }

        let blocks = LiveMarkdown.blocks(markdown)
        let editors = editors
        let done = await writer.write(manager, title: title, blocks: blocks) { editors.isStopped(ticket) }
        lastSeen = snapshot(editor)
        if !done { return .stopped }

        let written = manager.title
        return .written(documentId: editor.documentId, title: written.isEmpty ? (title ?? "") : written, blocks: blocks.count)
    }

    /// Runs `operation` in AI mode, after the calls before it have finished. Refused right away
    /// when the person stopped Claude Code a moment ago.
    private func inAiMode(_ operation: @escaping (_ ticket: Int) async -> LiveOutcome) async -> LiveOutcome {
        let previous = queue
        let editors = editors
        let task = Task { () -> LiveOutcome in
            await previous?.value
            guard let ticket = editors.beginWriting() else { return .stopped }
            let outcome = await operation(ticket)
            editors.endWriting(ticket)
            return editors.isStopped(ticket) ? .stopped : outcome
        }
        queue = Task { _ = await task.value }
        return await task.value
    }
}

private extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}
