import NoteEditor
import WriteopiaUI
import Writeopia
import Observation
import SwiftUI
import WrData
import WrDesign
import WrModels
import WrNetwork
import WrSession

@Observable
final class FolderContentsViewModel {
    private(set) var folders: [Folder] = []
    private(set) var documents: [WrDocument] = []
    private(set) var isLoading = false
    private(set) var isSyncing = false
    private(set) var errorMessage: String?
    /// Last sync failure; the local documents are shown anyway.
    private(set) var syncError: String?
    var actionError: String?

    // Selection by sliding the cards, like the notes list of the Compose app.
    private(set) var selectedIds: Set<String> = []
    private(set) var isSummarizing = false
    var summaryError: String?
    /// The summary in progress, awaited by tests.
    @ObservationIgnored private(set) var summaryTask: Task<Void, Never>?

    /// The folder shown, with its current title and icon; nil for the root.
    private(set) var folder: Folder?
    /// The folders from the root down to the parent of this one, for the breadcrumb.
    private(set) var ancestors: [Folder] = []

    let folderId: String
    let settings: FolderDisplaySettings
    let repository: DocumentsRepository
    private let aiClient: AiStreaming?

    init(
        folderId: String,
        folder: Folder? = nil,
        repository: DocumentsRepository,
        aiClient: AiStreaming? = nil,
        settings: FolderDisplaySettings = FolderDisplaySettings(preferences: nil)
    ) {
        self.folderId = folderId
        // The folder that was opened, so its menu works before the stored copy is read.
        self.folder = folder?.id == folderId ? folder : nil
        self.repository = repository
        self.aiClient = aiClient
        self.settings = settings
    }

    /// The "folder" that lists every favorite of the workspace.
    static let favoritesId = "wr.favorites"

    var isRoot: Bool { folderId == Folder.rootId }
    var isFavorites: Bool { folderId == Self.favoritesId }
    /// Items can be created and dropped only in a real folder.
    var canCreateItems: Bool { !isFavorites }

    var hasSelection: Bool { !selectedIds.isEmpty }
    var canSummarize: Bool { aiClient != nil }
    /// Folders can be selected, but not duplicated nor summarized: with one in the selection,
    /// only favorite and delete are offered, like the Compose app.
    var selectionHasFolders: Bool { folders.contains { selectedIds.contains($0.id) } }

    func select(_ id: String, _ selected: Bool) {
        if selected {
            selectedIds.insert(id)
        } else {
            selectedIds.remove(id)
        }
    }

    func toggleSelection(_ id: String) {
        if selectedIds.contains(id) {
            selectedIds.remove(id)
        } else {
            selectedIds.insert(id)
        }
    }

    func isSelected(_ id: String) -> Bool { selectedIds.contains(id) }

    func clearSelection() {
        selectedIds.removeAll()
    }

    /// Whether every selected item is a favorite, so the button removes them from the favorites.
    var selectionIsFavorite: Bool {
        let selected = items.filter { selectedIds.contains($0.id) }
        return !selected.isEmpty && selected.allSatisfy(\.isFavorite)
    }

    /// Copies the selected documents and folders, like "Copy" of the Compose selection menu.
    func copySelected() async {
        let ids = Array(selectedIds)
        clearSelection()
        await perform { try await self.repository.duplicate(ids: ids) }
    }

    /// Favorites the selection, or removes it from the favorites when it's all favorites already.
    func favoriteSelected() async {
        let ids = Array(selectedIds)
        let favorite = !selectionIsFavorite
        clearSelection()
        await perform { try await self.repository.setFavorite(ids: ids, favorite: favorite) }
    }

    /// The selected folders, which the picker mustn't offer as a destination.
    var selectedFolderIds: Set<String> {
        Set(folders.map(\.id)).intersection(selectedIds)
    }

    /// Moves every selected folder and document into `folderId`, like dragging the selection
    /// onto a folder in the Compose app. Each move bumps the item's timestamp, so the next sync
    /// sends it to the backend when it couldn't be sent right away.
    func moveSelected(to folderId: String) async {
        let ids = selectedIds.subtracting([folderId])
        clearSelection()
        guard !ids.isEmpty else { return }

        let movingFolders = Set(folders.map(\.id)).intersection(ids)
        folders.removeAll { ids.contains($0.id) }
        documents.removeAll { ids.contains($0.id) }

        var failure: String?
        for id in ids {
            do {
                if movingFolders.contains(id) {
                    try await repository.moveFolder(id: id, toFolder: folderId)
                } else {
                    try await repository.moveDocument(id: id, toFolder: folderId)
                }
            } catch {
                failure = failure ?? error.userMessage
            }
        }
        if let failure {
            actionError = failure
        }
        await load()
    }

    /// Deletes the selection (folders with everything inside).
    func deleteSelected() async {
        let ids = selectedIds
        clearSelection()
        folders.removeAll { ids.contains($0.id) }
        documents.removeAll { ids.contains($0.id) }
        await perform { try await self.repository.deleteItems(ids: Array(ids)) }
    }

    /// Dropped image files become one note each in this folder, an untitled document with the
    /// image as its only step, like `importImages` of the Compose notes menu.
    func importImages(_ urls: [URL]) async {
        let images = urls.filter { ImageProcessing.supportedExtensions.contains($0.pathExtension.lowercased()) }
        guard canCreateItems, !images.isEmpty else { return }

        for url in images {
            let accessing = url.startAccessingSecurityScopedResource()
            defer { if accessing { url.stopAccessingSecurityScopedResource() } }
            guard let data = try? Data(contentsOf: url), let jpeg = ImageProcessing.jpeg(from: data),
                  let file = try? ImageFiles.save(jpeg)
            else {
                actionError = String(localized: "\(url.lastPathComponent) can't be added as an image.")
                continue
            }

            await perform {
                var document = try await self.repository.createDocument(title: "", parentId: self.folderId)
                document.content.append(StoryStep(type: .image, path: file.path(percentEncoded: false), position: 1))
                try await self.repository.save(document)
            }
        }
        await load()
    }

    /// Summarizes the selected documents with the AI into a new document of this folder, like
    /// "AI Summary" of the Compose selection menu. Runs with Apple Intelligence on the device, or
    /// the cloud AI (see `AppSession.aiClient`).
    func summarizeSelected() {
        guard let aiClient, hasSelection else { return }
        let ids = documents.map(\.id).filter { selectedIds.contains($0) }
        clearSelection()
        guard !ids.isEmpty else {
            summaryError = String(localized: "Select at least one document to summarize.")
            return
        }

        summaryTask?.cancel()
        isSummarizing = true
        summaryTask = Task { await summarize(ids: ids, with: aiClient) }
    }

    /// Stops the summary in progress; nothing is saved.
    func cancelSummary() {
        summaryTask?.cancel()
        summaryTask = nil
    }

    private func summarize(ids: [String], with aiClient: AiStreaming) async {
        defer {
            isSummarizing = false
            summaryTask = nil
        }

        do {
            var prompt = ""
            for id in ids {
                let document = try await repository.document(id: id)
                prompt += "====================================================\n"
                prompt += DocumentToMarkdown.parse(document.content)
                prompt += "====================================================\n\n"
            }

            var answer = ""
            for try await partial in aiClient.stream(.summary, prompt: prompt) {
                answer = partial
            }
            // A cancelled stream just ends: don't mistake it for an empty answer.
            try Task.checkCancellation()

            guard let summary = MarkdownToDocument.read(answer, parentId: folderId, workspaceId: "") else {
                summaryError = String(localized: "The AI didn't return a summary.")
                return
            }
            try Task.checkCancellation()
            try await repository.save(summary)
            await load()
        } catch is CancellationError {
            return
        } catch {
            if Task.isCancelled { return }
            summaryError = error.userMessage
        }
    }

    var isEmpty: Bool { folders.isEmpty && documents.isEmpty }

    /// Folders and documents shown together, sorted as one list in the order chosen in the menu.
    var items: [FolderItem] {
        (folders.map(FolderItem.folder) + documents.map(FolderItem.document)).sorted(by: settings.order)
    }

    /// Shows what's on this device right away, then syncs the folder with the backend (open
    /// space) and shows the result, like the notes list of the Compose app.
    func load() async {
        isLoading = true
        defer { isLoading = false }

        await loadLocal()

        // Favorites come from what's synced already; a folder syncs itself.
        guard !isFavorites, let syncing = repository as? DocumentSyncing else { return }
        isSyncing = true
        defer { isSyncing = false }
        do {
            try await syncing.syncFolder(folderId)
            syncError = nil
            await loadLocal()
        } catch is CancellationError {
            return
        } catch {
            // Offline or the backend failed: what's on the device is still shown.
            syncError = error.userMessage
        }
    }

    func loadLocal() async {
        do {
            let contents = try isFavorites
                ? await repository.favorites()
                : await repository.folderContents(folderId: folderId)
            folders = contents.folders
            documents = contents.documents
            if !isRoot, !isFavorites {
                setPath(try await repository.folderPath(to: folderId))
            }
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.userMessage
        }
    }

    /// Creates a folder here with `icon`, picked in the same sheet that edits folders.
    func createFolder(title: String, icon: IconInfo? = nil) async {
        let created: Folder
        do {
            created = try await repository.createFolder(title: normalized(title, fallback: "New folder"), parentId: folderId)
        } catch {
            actionError = error.userMessage
            return
        }
        // The folder exists now: a failed icon doesn't make it look like the creation failed.
        if let icon {
            var withIcon = created
            withIcon.icon = icon
            if (try? await repository.updateFolder(withIcon)) == nil {
                actionError = String(localized: "The folder was created, but its icon couldn't be saved.")
            }
        }
        await load()
    }

    func createDocument(title: String) async -> WrDocument? {
        var created: WrDocument?
        await perform {
            created = try await self.repository.createDocument(
                title: self.normalized(title, fallback: "Untitled"),
                parentId: self.folderId
            )
        }
        return created
    }

    /// Moves the dragged item into `folder`. The item leaves the grid right away and the folder
    /// is reloaded afterwards, so its item count reflects the move.
    func move(_ payload: String, into folder: Folder) async -> Bool {
        guard let dragged = FolderItem.Payload(payload), dragged.id != folder.id else { return false }

        let previousFolders = folders
        let previousDocuments = documents
        switch dragged.kind {
        case .folder: folders.removeAll { $0.id == dragged.id }
        case .document: documents.removeAll { $0.id == dragged.id }
        }

        do {
            switch dragged.kind {
            case .folder: try await repository.moveFolder(id: dragged.id, toFolder: folder.id)
            case .document: try await repository.moveDocument(id: dragged.id, toFolder: folder.id)
            }
            await load()
            return true
        } catch let error as MoveError {
            folders = previousFolders
            documents = previousDocuments
            actionError = error.userMessage
        } catch {
            folders = previousFolders
            documents = previousDocuments
            actionError = error.userMessage
        }
        return false
    }

    // MARK: - Edition menu of the folder

    /// Renames the folder and changes its icon. An empty name keeps the current one.
    /// Returns whether the change was saved.
    @discardableResult
    func updateFolder(title: String, icon: IconInfo?) async -> Bool {
        guard var edited = folder else { return false }
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { edited.title = trimmed }
        edited.icon = icon

        let previous = folder
        folder = edited
        do {
            folder = try await repository.updateFolder(edited)
            return true
        } catch {
            folder = previous
            actionError = error.userMessage
            return false
        }
    }

    /// Moves this folder into `targetId`. Returns the folders from the root down to this one in
    /// its new place, to rebuild the navigation, or nil when it couldn't be moved.
    func moveFolder(to targetId: String) async -> [Folder]? {
        guard let folder, targetId != folder.parentId else { return nil }
        do {
            try await repository.moveFolder(id: folder.id, toFolder: targetId)
            let path = try await repository.folderPath(to: folder.id)
            setPath(path)
            return path
        } catch let error as MoveError {
            actionError = error.userMessage
        } catch {
            actionError = error.userMessage
        }
        return nil
    }

    /// Deletes this folder with everything inside it. Returns whether it was deleted.
    func deleteFolder() async -> Bool {
        guard !isRoot, !isFavorites else { return false }
        do {
            try await repository.deleteItems(ids: [folderId])
            return true
        } catch {
            actionError = error.userMessage
            return false
        }
    }

    private func setPath(_ path: [Folder]) {
        guard let current = path.last, current.id == folderId else { return }
        folder = current
        ancestors = Array(path.dropLast())
    }

    private func perform(_ operation: () async throws -> Void) async {
        do {
            try await operation()
            await load()
        } catch {
            actionError = error.userMessage
        }
    }

    private func normalized(_ title: String, fallback: String) -> String {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? fallback : trimmed
    }
}

public enum DocumentsRoute: Hashable {
    case folder(Folder)
    case document(id: String, title: String)
    /// Every favorite of the workspace, like "Favorites" of the Compose side menu.
    case favorites
}

/// Documents tab: the folders and documents of the current workspace.
public struct DocumentsRootView: View {
    @Environment(AppSession.self) private var session
    private let router: DocumentsRouter

    /// `router` is shared with the side menu of landscape, which navigates this tab too.
    public init(router: DocumentsRouter = DocumentsRouter()) {
        self.router = router
    }

    public var body: some View {
        DocumentsNavigation(session: session, router: router)
            // A different workspace means a different tree: start again from its root.
            .id("\(session.workspace?.id ?? "")-\(session.documentsVersion)")
            .onChange(of: session.workspace?.id) { router.reset() }
    }
}

private struct DocumentsNavigation: View {
    let session: AppSession
    @Bindable var router: DocumentsRouter
    @State private var settings: FolderDisplaySettings

    /// Ties a document card to the editor pushed from it, so the card zooms into the editor,
    /// like the shared element transition of the Compose app.
    @Namespace private var zoomNamespace

    init(session: AppSession, router: DocumentsRouter) {
        self.session = session
        self.router = router
        _settings = State(initialValue: FolderDisplaySettings(preferences: session.preferences))
    }

    var body: some View {
        let rootTitle = session.workspace?.name ?? String(localized: "Documents")
        NavigationStack(path: $router.path) {
            folderView(id: Folder.rootId, title: rootTitle, rootTitle: rootTitle)
                .navigationDestination(for: DocumentsRoute.self) { route in
                    switch route {
                    case .folder(let folder):
                        folderView(id: folder.id, folder: folder, title: folder.displayTitle, rootTitle: rootTitle)
                    case .favorites:
                        folderView(id: FolderContentsViewModel.favoritesId, title: String(localized: "Favorites"), rootTitle: rootTitle)
                    case .document(let id, let title):
                        NoteEditorView(
                            documentId: id,
                            title: title,
                            repository: session.documents,
                            aiClient: session.aiClient,
                            publishing: session.publishing,
                            imageUploader: session.imageUploader,
                            isPremium: session.user?.isPremium ?? false,
                            presentationsEnabled: session.supportsPresentations
                        ) { link in
                            router.path.append(DocumentsRoute.document(id: link.id, title: link.title ?? "Untitled"))
                        }
                        .zoomTransition(sourceID: id, in: zoomNamespace)
                    }
                }
        }
    }

    private func folderView(id: String, folder: Folder? = nil, title: String, rootTitle: String) -> FolderContentsView {
        FolderContentsView(
            folderId: id,
            folder: folder,
            title: title,
            rootTitle: rootTitle,
            repository: session.documents,
            aiClient: session.aiClient,
            settings: settings,
            path: $router.path,
            contentsVersion: router.contentsVersion,
            onItemsChange: router.treeChanged,
            transitionNamespace: zoomNamespace
        )
    }
}

struct FolderContentsView: View {
    @State private var viewModel: FolderContentsViewModel
    @Binding private var path: NavigationPath
    @State private var newItem: NewItem?
    @State private var showsNewFolder = false
    @State private var showsMoveSelection = false
    @State private var newItemTitle = ""
    /// Folder currently under a drag, highlighted as the drop target.
    @State private var dropTargetId: String?
    @State private var movedCount = 0
    @State private var swipeSelection = SwipeSelectionCoordinator()
    @State private var dragSelection = DragSelection()
    @State private var folderSheet: FolderSheet?
    @State private var confirmsFolderDeletion = false
    private let title: String
    private let rootTitle: String
    private let contentsVersion: Int
    private let onItemsChange: () -> Void
    /// Namespace of the zoom into the editor; nil where the cards don't push the editor.
    private let transitionNamespace: Namespace.ID?

    /// Cards are at least this wide and fill the width, so wider windows get more columns, like
    /// the adaptive grids of the Compose app (220 on desktop, 150 on phones and tablets).
    private static var minCardWidth: CGFloat {
        #if os(macOS)
        220
        #else
        150
        #endif
    }

    private static let gridSpacing: CGFloat = 12
    private let columns = [GridItem(.adaptive(minimum: FolderContentsView.minCardWidth), spacing: FolderContentsView.gridSpacing)]
    @State private var contentWidth: CGFloat = 0

    /// New documents are named in an alert; new folders use `FolderEditSheet`, to pick an icon.
    private enum NewItem: String, Identifiable {
        case document = "New document"

        var id: String { rawValue }
    }

    init(
        folderId: String,
        folder: Folder? = nil,
        title: String,
        rootTitle: String,
        repository: DocumentsRepository,
        aiClient: AiStreaming? = nil,
        settings: FolderDisplaySettings,
        path: Binding<NavigationPath>,
        contentsVersion: Int = 0,
        onItemsChange: @escaping () -> Void = {},
        transitionNamespace: Namespace.ID? = nil
    ) {
        _viewModel = State(
            initialValue: FolderContentsViewModel(folderId: folderId, folder: folder, repository: repository, aiClient: aiClient, settings: settings)
        )
        _path = path
        self.title = title
        self.rootTitle = rootTitle
        self.contentsVersion = contentsVersion
        self.onItemsChange = onItemsChange
        self.transitionNamespace = transitionNamespace
    }

    private var settings: FolderDisplaySettings { viewModel.settings }

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    // Inside the scroll view, not in a top inset: an inset hides the large title on iOS 26+.
                    if !viewModel.isRoot {
                        FolderBreadcrumb(
                            rootTitle: rootTitle,
                            ancestors: viewModel.ancestors,
                            current: viewModel.folder?.displayTitle ?? title,
                            onSelect: navigate(toAncestor:)
                        )
                    }
                    arrangedItems
                        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { contentWidth = $0 }
                        .padding([.horizontal, .bottom])
                        .padding(.top, viewModel.isRoot ? 16 : 4)
                        .background { SwipeSelectionInstaller(coordinator: swipeSelection) }
                        .environment(\.swipeSelection, swipeSelection)
                }
                // The content fills the visible height, so a drag can start anywhere on the
                // empty space and select the items it crosses, like the Compose app.
                .frame(maxWidth: .infinity, minHeight: geometry.size.height, alignment: .top)
                .contentShape(Rectangle())
                .dragSelectionBox(dragSelection)
                // Image files dropped from the Finder become notes, like the Compose app.
                .dropDestination(for: URL.self) { urls, _ in
                    let images = urls.filter { ImageProcessing.supportedExtensions.contains($0.pathExtension.lowercased()) }
                    guard !images.isEmpty else { return false }
                    Task { await viewModel.importImages(images) }
                    return true
                }
                .onAppear {
                    dragSelection.onChange = { [viewModel] id, isInside in viewModel.select(id, isInside) }
                    swipeSelection.onScrollViewFound = { [dragSelection] scrollView in dragSelection.scrollView = scrollView }
                    if let scrollView = swipeSelection.scrollView { dragSelection.scrollView = scrollView }
                }
            }
        }
        // On the scroll view only: applied to the whole screen, the sheets would inherit it and
        // get a pull to refresh of their own.
        .refreshable { await viewModel.load() }
        .background(WrColors.background)
        .modifier(folderMenuPresentations)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if viewModel.hasSelection {
                DocumentsSelectionMenu(
                    count: viewModel.selectedIds.count,
                    isFavorite: viewModel.selectionIsFavorite,
                    showsCopy: !viewModel.selectionHasFolders,
                    showsSummary: viewModel.canSummarize && !viewModel.selectionHasFolders,
                    onCopy: { Task { await viewModel.copySelected() } },
                    onFavorite: { Task { await viewModel.favoriteSelected() } },
                    onSummary: { viewModel.summarizeSelected() },
                    onMove: { showsMoveSelection = true },
                    onDelete: { Task { await viewModel.deleteSelected() } },
                    onClose: { withAnimation(.snappy) { viewModel.clearSelection() } }
                )
                .transition(.move(edge: .bottom).combined(with: .opacity))
            } else if viewModel.isSummarizing {
                HStack(spacing: 10) {
                    ProgressView()
                        .controlSize(.small)
                    Label("Summarizing…", systemImage: "sparkles")
                        .font(.subheadline.weight(.semibold))
                    Button {
                        withAnimation(.snappy) { viewModel.cancelSummary() }
                    } label: {
                        Image(systemName: "xmark")
                            .font(.footnote.weight(.bold))
                            .frame(width: 28, height: 28)
                            .contentShape(Circle())
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(.secondary)
                    .help("Cancel summary")
                    .accessibilityLabel("Cancel summary")
                    .accessibilityIdentifier("documents.summary.cancel")
                }
                .padding(.leading, 16)
                .padding(.trailing, 6)
                .frame(height: 44)
                .background(.regularMaterial, in: Capsule())
                .padding(.bottom, 8)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.snappy, value: viewModel.hasSelection)
        .animation(.snappy, value: viewModel.isSummarizing)
        .alert(
            "Could not summarize",
            isPresented: Binding(get: { viewModel.summaryError != nil }, set: { if !$0 { viewModel.summaryError = nil } })
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.summaryError ?? "")
        }
        .animation(.snappy, value: viewModel.items.map(\.id))
        // Keeps the folder tree of the side menu in step.
        .onChange(of: viewModel.items.map(\.id)) { onItemsChange() }
        .animation(.snappy, value: settings.arrangement)
        .animation(.snappy, value: dropTargetId)
        .sensoryFeedback(.success, trigger: movedCount)
        .overlay {
            WrStateOverlay(
                isLoading: viewModel.isLoading,
                isEmpty: viewModel.isEmpty,
                errorMessage: viewModel.errorMessage,
                emptyTitle: "This folder is empty",
                emptyImage: "folder",
                retry: { Task { await viewModel.load() } }
            )
        }
        .navigationTitle(viewModel.folder?.displayTitle ?? title)
        // A large title can't show an icon, so inside a folder the title is inline, with the icon.
        .toolbarTitleDisplayMode(viewModel.isRoot ? .automatic : .inline)
        .toolbar {
            if !viewModel.isRoot, !viewModel.isFavorites {
                ToolbarItem(placement: .principal) {
                    folderTitle
                }
            }
            ToolbarItem(placement: .primaryAction) {
                folderMenu
            }
            ToolbarItem(placement: .wrTrailing) {
                if viewModel.isSyncing {
                    ProgressView()
                        .controlSize(.small)
                        .accessibilityLabel("Syncing")
                }
            }
            if viewModel.canCreateItems {
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        Button {
                            present(.document)
                        } label: {
                            Label("New document", systemImage: "doc.badge.plus")
                        }
                        Button {
                            showsNewFolder = true
                        } label: {
                            Label("New folder", systemImage: "folder.badge.plus")
                        }
                    } label: {
                        Label("Add", systemImage: "plus")
                    }
                    .accessibilityIdentifier("documents.add")
                }
            }
        }
        .task { await viewModel.load() }
        // Something outside the list, like the side menu, changed the folders.
        .onChange(of: contentsVersion) { Task { await viewModel.loadLocal() } }
        .alert(
            newItem?.rawValue ?? "",
            isPresented: Binding(get: { newItem != nil }, set: { if !$0 { newItem = nil } }),
            presenting: newItem
        ) { item in
            TextField("Title", text: $newItemTitle)
            Button("Cancel", role: .cancel) {}
            Button("Create") { create(item) }
        }
        .sheet(isPresented: $showsMoveSelection) {
            FolderPickerSheet(
                excludedFolderIds: viewModel.selectedFolderIds,
                currentParentId: viewModel.folderId,
                rootTitle: rootTitle,
                repository: viewModel.repository
            ) { folderId in
                Task { await viewModel.moveSelected(to: folderId) }
            }
            .wrSheetSize(width: 440, height: 520)
        }
        .sheet(isPresented: $showsNewFolder) {
            FolderEditSheet(folder: nil) { title, icon in
                Task { await viewModel.createFolder(title: title, icon: icon) }
            }
            .presentationDetents([.medium, .large])
            .wrSheetSize(width: 440, height: 440)
        }
        .alert(
            "Something went wrong",
            isPresented: Binding(
                get: { viewModel.actionError != nil },
                set: { if !$0 { viewModel.actionError = nil } }
            )
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.actionError ?? "")
        }
    }

    // MARK: - Layouts

    /// The items as a list, a grid or a staggered grid, like the arrangements of the Compose app.
    @ViewBuilder
    private var arrangedItems: some View {
        switch settings.arrangement {
        case .list:
            // Folders and documents in sections of their own, each in the order chosen.
            LazyVStack(alignment: .leading, spacing: 8) {
                listSection("Folders", items: viewModel.items.filter(\.isFolder))
                listSection("Documents", items: viewModel.items.filter { !$0.isFolder })
            }
        case .grid:
            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(viewModel.items) { cell($0, style: .grid) }
            }
        case .staggeredGrid:
            HStack(alignment: .top, spacing: 12) {
                ForEach(Array(staggeredColumns.enumerated()), id: \.offset) { _, column in
                    LazyVStack(spacing: 12) {
                        ForEach(column) { cell($0, style: .staggered) }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func listSection(_ title: LocalizedStringKey, items: [FolderItem]) -> some View {
        if !items.isEmpty {
            Section {
                ForEach(items) { cell($0, style: .row) }
            } header: {
                Text(title)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(WrColors.textLighter)
                    .textCase(.uppercase)
                    .padding(.horizontal, 4)
                    .padding(.top, 8)
                    .accessibilityAddTraits(.isHeader)
            }
        }
    }

    /// Items spread over the columns of the staggered grid, each going to the shortest column so
    /// they stay balanced.
    private var staggeredColumns: [[FolderItem]] {
        // As many columns as cards of the minimum width fit, at least two.
        let fitting = Int((contentWidth + Self.gridSpacing) / (Self.minCardWidth + Self.gridSpacing))
        let count = max(2, fitting)
        var columns = Array(repeating: [FolderItem](), count: count)
        var heights = Array(repeating: 0, count: count)
        for item in viewModel.items {
            let shortest = heights.indices.min { heights[$0] < heights[$1] } ?? 0
            columns[shortest].append(item)
            heights[shortest] += item.estimatedStaggeredHeight
        }
        return columns
    }

    private func cell(_ item: FolderItem, style: ItemCard.Style) -> some View {
        NavigationLink(value: item.route) {
            ItemCard(item: item, isDropTarget: dropTargetId == item.id, isSelected: viewModel.isSelected(item.id), style: style)
        }
        .buttonStyle(.plain)
        .zoomTransitionSource(id: item.id, in: transitionNamespace, enabled: !item.isFolder)
        // Slide a card sideways to select it, like the Compose notes list.
        .slideToSelect { viewModel.toggleSelection(item.id) }
        .selectableByDrag(id: item.id)
        .accessibilityAction(named: viewModel.isSelected(item.id) ? "Unselect" : "Select") {
            viewModel.toggleSelection(item.id)
        }
        .draggable(item.payload.rawValue) {
            ItemCard(item: item, isDropTarget: false, isSelected: false)
                .frame(width: 160)
        }
        .folderDropDestination(item) { payload, folder in
            Task {
                if await viewModel.move(payload, into: folder) {
                    movedCount += 1
                }
            }
        } isTargeted: { targeted in
            if targeted {
                dropTargetId = item.id
            } else if dropTargetId == item.id {
                dropTargetId = nil
            }
        }
    }

    /// The icon and the name of the folder shown, in the navigation bar.
    private var folderTitle: some View {
        HStack(spacing: 6) {
            FolderIconImage(icon: viewModel.folder?.icon)
            Text(viewModel.folder?.displayTitle ?? title)
                .fontWeight(.semibold)
                .lineLimit(1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
        .accessibilityIdentifier("documents.folderTitle")
    }

    // MARK: - Edition menu of the folder

    /// Opens the bottom sheet with how the folder is shown and sorted and, inside a folder,
    /// editing, moving and deleting it, like the edition menu of the Compose notes list.
    private var folderMenu: some View {
        Button {
            folderSheet = .options
        } label: {
            Label("Folder options", systemImage: "ellipsis.circle")
        }
        .accessibilityIdentifier("documents.folderMenu")
    }

    private var folderMenuPresentations: FolderMenuPresentations {
        FolderMenuPresentations(
            isFolder: !viewModel.isRoot && !viewModel.isFavorites,
            folderTitle: viewModel.folder?.displayTitle ?? title,
            folder: viewModel.folder,
            rootTitle: rootTitle,
            settings: settings,
            repository: viewModel.repository,
            sheet: $folderSheet,
            confirmsDeletion: $confirmsFolderDeletion,
            onEdit: { title, icon in
                Task {
                    // The title and icon show in the side menu tree too.
                    if await viewModel.updateFolder(title: title, icon: icon) {
                        onItemsChange()
                    }
                }
            },
            onMove: { targetId in
                Task {
                    if let newPath = await viewModel.moveFolder(to: targetId) {
                        // The back button now goes through the folders it's in after the move.
                        path = NavigationPath(newPath.map(DocumentsRoute.folder))
                        // Its place in the side menu tree changed, not the items it shows.
                        onItemsChange()
                    }
                }
            },
            onDelete: {
                Task {
                    if await viewModel.deleteFolder(), !path.isEmpty {
                        path.removeLast()
                    }
                }
            }
        )
    }

    /// Goes back to a folder of the breadcrumb; nil is the root.
    private func navigate(toAncestor folder: Folder?) {
        guard let folder, let index = viewModel.ancestors.firstIndex(where: { $0.id == folder.id }) else {
            path = NavigationPath()
            return
        }
        path = NavigationPath(viewModel.ancestors.prefix(through: index).map(DocumentsRoute.folder))
    }

    private func present(_ item: NewItem) {
        newItemTitle = ""
        newItem = item
    }

    private func create(_ item: NewItem) {
        let title = newItemTitle
        Task {
            switch item {
            case .document:
                if let document = await viewModel.createDocument(title: title) {
                    path.append(DocumentsRoute.document(id: document.id, title: document.displayTitle))
                }
            }
        }
    }
}

enum FolderItem: Identifiable {
    case folder(Folder)
    case document(WrDocument)

    var id: String {
        switch self {
        case .folder(let folder): folder.id
        case .document(let document): document.id
        }
    }

    /// What is carried while dragging an item around the grid.
    struct Payload: Equatable {
        enum Kind: String {
            case folder
            case document
        }

        private static let prefix = "writeopia-item"

        let kind: Kind
        let id: String

        init(kind: Kind, id: String) {
            self.kind = kind
            self.id = id
        }

        /// Parses a dropped string. Text dragged from other apps is ignored.
        init?(_ rawValue: String) {
            let parts = rawValue.split(separator: ":", maxSplits: 2).map(String.init)
            guard parts.count == 3, parts[0] == Self.prefix, let kind = Kind(rawValue: parts[1]), !parts[2].isEmpty else {
                return nil
            }
            self.init(kind: kind, id: parts[2])
        }

        var rawValue: String { "\(Self.prefix):\(kind.rawValue):\(id)" }
    }

    var payload: Payload {
        switch self {
        case .folder(let folder): Payload(kind: .folder, id: folder.id)
        case .document(let document): Payload(kind: .document, id: document.id)
        }
    }

    var isFolder: Bool {
        if case .folder = self { true } else { false }
    }

    var isFavorite: Bool {
        switch self {
        case .folder(let folder): folder.favorite
        case .document(let document): document.isFavorite
        }
    }

    var route: DocumentsRoute {
        switch self {
        case .folder(let folder): .folder(folder)
        case .document(let document): .document(id: document.id, title: document.displayTitle)
        }
    }
}

/// A folder or a document in the folder grid, the staggered grid or the list.
struct ItemCard: View {
    enum Style {
        case grid
        case staggered
        case row
    }

    let item: FolderItem
    let isDropTarget: Bool
    var isSelected = false
    var style: Style = .grid

    var body: some View {
        Group {
            if style == .row {
                row
            } else {
                card
            }
        }
        .background(
            isDropTarget || isSelected ? WrColors.accent.opacity(0.15) : WrColors.surface,
            in: RoundedRectangle(cornerRadius: cornerRadius)
        )
        .overlay {
            RoundedRectangle(cornerRadius: cornerRadius)
                .strokeBorder(
                    isDropTarget || isSelected ? WrColors.accent : WrColors.divider,
                    lineWidth: isDropTarget || isSelected ? 2 : 1
                )
        }
        .overlay(alignment: style == .row ? .trailing : .topTrailing) {
            if isSelected {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(WrColors.accent)
                    .padding(style == .row ? 12 : 8)
                    .transition(.scale.combined(with: .opacity))
            }
        }
        .animation(.snappy, value: isSelected)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .scaleEffect(isDropTarget ? (style == .row ? 1.02 : 1.04) : 1)
        .contentShape(RoundedRectangle(cornerRadius: cornerRadius))
    }

    private var cornerRadius: CGFloat { style == .row ? 12 : 16 }

    private var card: some View {
        VStack(alignment: .leading, spacing: 0) {
            // A document with a colored header shows it across the top of the card, with the
            // title on it, like `HeaderPreviewDrawer` of the Compose app.
            if let headerColor {
                coloredHeader(headerColor)
            }

            VStack(alignment: .leading, spacing: 8) {
                if headerColor == nil {
                    HStack(alignment: .top) {
                        iconImage
                            .font(.title2)
                        Spacer()
                        favoriteStar
                    }

                    titleText
                }

                if let preview, !preview.isEmpty {
                    Text(preview)
                        .font(.caption)
                        .foregroundStyle(WrColors.textLighter)
                        .lineLimit(style == .staggered ? 10 : 3)
                        .multilineTextAlignment(.leading)
                }

                if style == .grid {
                    Spacer(minLength: 0)
                }

                footer
                    .font(.caption2)
                    .foregroundStyle(WrColors.textLighter)
            }
            .padding(14)
        }
        .frame(maxWidth: .infinity, minHeight: style == .grid ? 150 : nil, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
    }

    private func coloredHeader(_ color: Color) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            iconImage
                .font(.title3)
            titleText
        }
        .padding(14)
        .frame(maxWidth: .infinity, minHeight: 70, alignment: .bottomLeading)
        .background(color)
        .overlay(alignment: .topTrailing) {
            favoriteStar
                .padding(8)
        }
    }

    private var titleText: some View {
        Text(title)
            .font(.headline)
            .foregroundStyle(WrColors.textLight)
            .lineLimit(style == .staggered ? 4 : 2)
            .multilineTextAlignment(.leading)
    }

    @ViewBuilder
    private var favoriteStar: some View {
        if isFavorite {
            Image(systemName: "star.fill")
                .font(.caption)
                .foregroundStyle(.yellow)
        }
    }

    private var row: some View {
        HStack(spacing: 12) {
            iconImage
                .font(.title3)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(title)
                        .font(.body.weight(.medium))
                        .foregroundStyle(WrColors.textLight)
                        .lineLimit(1)
                    if isFavorite {
                        Image(systemName: "star.fill")
                            .font(.caption)
                            .foregroundStyle(.yellow)
                    }
                }
                if let preview, !preview.isEmpty {
                    Text(preview)
                        .font(.subheadline)
                        .foregroundStyle(WrColors.textLighter)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 8)

            footer
                .font(.caption)
                .foregroundStyle(WrColors.textLighter)
                .opacity(isSelected ? 0 : 1)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private var iconImage: some View {
        switch item {
        case .folder(let folder):
            FolderIconImage(icon: folder.icon, isDropTarget: isDropTarget)
        case .document:
            Image(systemName: "doc.text")
                .foregroundStyle(WrColors.textLighter)
        }
    }

    private var title: String {
        switch item {
        case .folder(let folder): folder.displayTitle
        case .document(let document): document.displayTitle
        }
    }

    private var isFavorite: Bool {
        switch item {
        case .folder(let folder): folder.favorite
        case .document(let document): document.isFavorite
        }
    }

    private var preview: String? {
        if case .document(let document) = item { document.preview } else { nil }
    }

    private var headerColor: Color? {
        if case .document(let document) = item { document.headerColor.map { Color(argb: $0) } } else { nil }
    }

    @ViewBuilder
    private var footer: some View {
        switch item {
        case .folder(let folder):
            Text("\(folder.itemCount) items")
        case .document(let document):
            if document.lastUpdatedAt > 0 {
                Text(document.lastUpdatedDate, format: .relative(presentation: .named))
            }
        }
    }
}

extension FolderItem {
    var title: String {
        switch self {
        case .folder(let folder): folder.title
        case .document(let document): document.title
        }
    }

    /// Epoch milliseconds, the way documents keep their dates.
    var createdAtMillis: Int64 {
        switch self {
        case .folder(let folder): folder.createdAt?.millis ?? 0
        case .document(let document): document.createdAt
        }
    }

    var lastUpdatedAtMillis: Int64 {
        switch self {
        case .folder(let folder): folder.lastUpdatedAt?.millis ?? 0
        case .document(let document): document.lastUpdatedAt
        }
    }
}

extension [FolderItem] {
    /// Folders and documents sorted together, like `sortedWithOrderBy` of the Compose app: the
    /// newest first for dates, alphabetically for names. Ties keep a fixed order by id.
    func sorted(by order: DocumentsOrder) -> [FolderItem] {
        sorted { first, second in
            switch order {
            case .updated where first.lastUpdatedAtMillis != second.lastUpdatedAtMillis:
                return first.lastUpdatedAtMillis > second.lastUpdatedAtMillis
            case .created where first.createdAtMillis != second.createdAtMillis:
                return first.createdAtMillis > second.createdAtMillis
            case .name:
                let comparison = first.title.localizedCaseInsensitiveCompare(second.title)
                if comparison != .orderedSame { return comparison == .orderedAscending }
            default:
                break
            }
            return first.id < second.id
        }
    }
}

extension FolderItem {
    /// Rough height of the card in the staggered grid, to balance its columns.
    var estimatedStaggeredHeight: Int {
        switch self {
        case .folder: 100
        case .document(let document): 100 + min(document.preview.count, 400) / 2
        }
    }
}

extension View {
    /// Marks the view as the origin of a zoom into the screen pushed from it, like the shared
    /// element transition of the Compose app. iOS 18 has the transition; the Mac has no zoom for
    /// a pushed screen (`NavigationTransition.zoom` is unavailable there), and older iOS keeps
    /// the default push.
    @ViewBuilder
    func zoomTransitionSource(id: String, in namespace: Namespace.ID?, enabled: Bool) -> some View {
        #if os(iOS)
        if enabled, let namespace, #available(iOS 18, *) {
            matchedTransitionSource(id: id, in: namespace)
        } else {
            self
        }
        #else
        self
        #endif
    }

    /// Zooms the pushed screen out of the view marked with `zoomTransitionSource`.
    @ViewBuilder
    func zoomTransition(sourceID: String, in namespace: Namespace.ID) -> some View {
        #if os(iOS)
        if #available(iOS 18, *) {
            navigationTransition(.zoom(sourceID: sourceID, in: namespace))
        } else {
            self
        }
        #else
        self
        #endif
    }

    /// Makes folder cards accept dragged items. Documents are not drop targets.
    @ViewBuilder
    func folderDropDestination(
        _ item: FolderItem,
        onDrop: @escaping (String, Folder) -> Void,
        isTargeted: @escaping (Bool) -> Void
    ) -> some View {
        if case .folder(let folder) = item {
            dropDestination(for: String.self) { payloads, _ in
                guard let payload = payloads.first,
                      let dragged = FolderItem.Payload(payload),
                      dragged.id != folder.id
                else { return false }

                onDrop(payload, folder)
                return true
            } isTargeted: { targeted in
                isTargeted(targeted)
            }
        } else {
            self
        }
    }
}

public struct DocumentRow: View {
    let document: WrDocument

    public init(document: WrDocument) {
        self.document = document
    }

    public var body: some View {
        Label {
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(document.displayTitle)
                        .font(.body.weight(.medium))
                        .foregroundStyle(WrColors.textLight)
                        .lineLimit(1)
                    if document.isFavorite {
                        Image(systemName: "star.fill")
                            .font(.caption)
                            .foregroundStyle(.yellow)
                    }
                }

                if !document.preview.isEmpty {
                    Text(document.preview)
                        .font(.subheadline)
                        .foregroundStyle(WrColors.textLighter)
                        .lineLimit(2)
                }

                if document.lastUpdatedAt > 0 {
                    Text(document.lastUpdatedDate, format: .relative(presentation: .named))
                        .font(.caption)
                        .foregroundStyle(WrColors.textLighter)
                }
            }
        } icon: {
            Image(systemName: "doc.text")
                .foregroundStyle(WrColors.textLighter)
        }
    }
}
