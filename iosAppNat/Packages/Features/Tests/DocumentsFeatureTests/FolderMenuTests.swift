import Foundation
import Testing
@testable import DocumentsFeature
import WrData
import WrModels
import WrNetwork
import WrStorage

@Suite struct FolderMenuTests {
    let work = Folder(id: "work", parentId: Folder.rootId, title: "Work", workspaceId: "w")
    let ideas = Folder(id: "ideas", parentId: "work", title: "Ideas", workspaceId: "w")
    let archive = Folder(id: "archive", parentId: Folder.rootId, title: "Archive", workspaceId: "w")

    private func makeViewModel(folderId: String = "ideas", settings: FolderDisplaySettings = FolderDisplaySettings(preferences: nil)) async
        -> (FolderContentsViewModel, FakeDocumentsRepository) {
        let repository = FakeDocumentsRepository()
        repository.foldersById = [work.id: work, ideas.id: ideas, archive.id: archive]
        let viewModel = FolderContentsViewModel(folderId: folderId, repository: repository, settings: settings)
        await viewModel.load()
        return (viewModel, repository)
    }

    @Test func showsWhereTheFolderIs() async {
        let (viewModel, _) = await makeViewModel()

        #expect(viewModel.folder?.title == "Ideas")
        #expect(viewModel.ancestors.map(\.id) == ["work"])
    }

    @Test func openedFolderCanBeEditedBeforeItLoads() {
        let viewModel = FolderContentsViewModel(folderId: "ideas", folder: ideas, repository: FakeDocumentsRepository())

        #expect(viewModel.folder?.id == "ideas")
    }

    @Test func rootHasNoFolderToEdit() async {
        let (viewModel, _) = await makeViewModel(folderId: Folder.rootId)

        #expect(viewModel.isRoot)
        #expect(viewModel.folder == nil)
        #expect(viewModel.ancestors.isEmpty)
    }

    @Test func editsNameAndIcon() async {
        let (viewModel, repository) = await makeViewModel()

        await viewModel.updateFolder(title: "  Plans ", icon: IconInfo(label: "star", tint: -256))

        #expect(repository.updatedFolders.last?.title == "Plans")
        #expect(repository.updatedFolders.last?.icon == IconInfo(label: "star", tint: -256))
        #expect(viewModel.folder?.title == "Plans")
    }

    @Test func emptyNameKeepsTheCurrentOne() async {
        let (viewModel, repository) = await makeViewModel()

        await viewModel.updateFolder(title: "   ", icon: nil)

        #expect(repository.updatedFolders.last?.title == "Ideas")
    }

    @Test func failedEditIsUndone() async {
        let (viewModel, repository) = await makeViewModel()
        repository.updateError = APIError.notFound

        await viewModel.updateFolder(title: "Plans", icon: nil)

        #expect(viewModel.folder?.title == "Ideas")
        #expect(viewModel.actionError != nil)
    }

    @Test func movesToAnotherFolderAndReturnsTheNewPath() async {
        let (viewModel, repository) = await makeViewModel()

        let path = await viewModel.moveFolder(to: "archive")

        #expect(repository.moves == ["folder ideas -> archive"])
        #expect(path?.map(\.id) == ["archive", "ideas"])
        #expect(viewModel.ancestors.map(\.id) == ["archive"])
    }

    @Test func movingToTheSameParentDoesNothing() async {
        let (viewModel, repository) = await makeViewModel()

        #expect(await viewModel.moveFolder(to: "work") == nil)
        #expect(repository.moves.isEmpty)
    }

    @Test func failedMoveShowsTheError() async {
        let (viewModel, repository) = await makeViewModel()
        repository.moveError = MoveError.folderIntoItself

        #expect(await viewModel.moveFolder(to: "archive") == nil)
        #expect(viewModel.actionError == MoveError.folderIntoItself.userMessage)
    }

    @Test func deletesTheFolderWithoutReadingIt() async {
        let repository = FakeDocumentsRepository()
        let viewModel = FolderContentsViewModel(folderId: "ideas", repository: repository)

        #expect(await viewModel.deleteFolder())
        #expect(repository.deleted == [["ideas"]])
    }

    @Test func deletesTheFolder() async {
        let (viewModel, repository) = await makeViewModel()

        #expect(await viewModel.deleteFolder())
        #expect(repository.deleted == [["ideas"]])
    }

    @Test func foldersAndDocumentsAreSortedTogether() async {
        let settings = FolderDisplaySettings(preferences: nil)
        let repository = FakeDocumentsRepository()
        repository.contents = FolderContents(
            folders: [
                Folder(id: "beta", parentId: Folder.rootId, title: "Beta", workspaceId: "w",
                       createdAt: Date(millis: 400), lastUpdatedAt: Date(millis: 100)),
                Folder(id: "alpha", parentId: Folder.rootId, title: "alpha", workspaceId: "w",
                       createdAt: Date(millis: 100), lastUpdatedAt: Date(millis: 500)),
            ],
            documents: [
                WrDocument(id: "zebra", title: "Zebra", workspaceId: "w", createdAt: 300, lastUpdatedAt: 900),
                WrDocument(id: "apple", title: "Apple", workspaceId: "w", createdAt: 200, lastUpdatedAt: 50),
            ]
        )
        let viewModel = FolderContentsViewModel(folderId: Folder.rootId, repository: repository, settings: settings)
        await viewModel.load()

        #expect(viewModel.items.map(\.id) == ["zebra", "alpha", "beta", "apple"])
        settings.order = .created
        #expect(viewModel.items.map(\.id) == ["beta", "zebra", "apple", "alpha"])
        settings.order = .name
        #expect(viewModel.items.map(\.id) == ["alpha", "apple", "beta", "zebra"])
    }

    @Test func displaySettingsAreKept() throws {
        let defaults = try #require(UserDefaults(suiteName: "folder-menu-\(UUID().uuidString)"))
        let preferences = Preferences(defaults: defaults)

        let settings = FolderDisplaySettings(preferences: preferences)
        #expect(settings.arrangement == .grid)
        #expect(settings.order == .updated)
        settings.arrangement = .staggeredGrid
        settings.order = .name

        let reopened = FolderDisplaySettings(preferences: preferences)
        #expect(reopened.arrangement == .staggeredGrid)
        #expect(reopened.order == .name)
        #expect(defaults.string(forKey: Preferences.Key.documentsArrangement.rawValue) == "staggered_grid")
    }

    @Test func iconsUseTheComposeNamesAndTints() {
        #expect(FolderIcons.symbol(for: "home") == "house")
        #expect(FolderIcons.symbol(for: "unknown") == nil)
        #expect(Set(FolderIcons.all.map(\.name)).count == FolderIcons.all.count)
        // `Color.LightGray.toArgb()` and `Color.Red.toArgb()` of Compose.
        #expect(FolderIcons.tints.first == -3_355_444)
        #expect(FolderIcons.tints.contains(-65_536))
    }
}
