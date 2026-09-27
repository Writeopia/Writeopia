import Foundation
import Testing
@testable import DocumentsFeature
import WrData
import WrModels
import WrNetwork

final class FakeDocumentsRepository: DocumentsRepository {
    var contents = FolderContents()
    var moves: [String] = []
    var moveError: Error?

    func folderContents(folderId: String) async throws -> FolderContents { contents }
    func document(id: String) async throws -> WrDocument { throw APIError.notFound }
    func search(query: String) async throws -> [WrDocument] { [] }
    func createFolder(title: String, parentId: String) async throws -> Folder { throw APIError.notFound }
    func createDocument(title: String, parentId: String) async throws -> WrDocument { throw APIError.notFound }

    func moveDocument(id: String, toFolder folderId: String) async throws {
        if let moveError { throw moveError }
        moves.append("document \(id) -> \(folderId)")
        contents.documents.removeAll { $0.id == id }
    }

    func moveFolder(id: String, toFolder folderId: String) async throws {
        if let moveError { throw moveError }
        moves.append("folder \(id) -> \(folderId)")
        contents.folders.removeAll { $0.id == id }
    }

    func save(_ document: WrDocument) async throws {}
}

@Suite struct FolderMoveTests {
    let ideas = Folder(id: "f1", parentId: Folder.rootId, title: "Ideas", workspaceId: "w")
    let work = Folder(id: "f2", parentId: Folder.rootId, title: "Work", workspaceId: "w")
    let draft = WrDocument(id: "d1", title: "Draft", workspaceId: "w", parentId: Folder.rootId)

    private func makeViewModel() async -> (FolderContentsViewModel, FakeDocumentsRepository) {
        let repository = FakeDocumentsRepository()
        repository.contents = FolderContents(folders: [ideas, work], documents: [draft])
        let viewModel = FolderContentsViewModel(folderId: Folder.rootId, repository: repository)
        await viewModel.load()
        return (viewModel, repository)
    }

    @Test func payloadRoundTrips() {
        let payload = FolderItem.document(draft).payload
        #expect(FolderItem.Payload(payload.rawValue) == payload)
        #expect(FolderItem.Payload("some text from another app") == nil)
        #expect(FolderItem.Payload("writeopia-item:unknown:x") == nil)
    }

    @Test func dropsDocumentIntoFolder() async {
        let (viewModel, repository) = await makeViewModel()

        let moved = await viewModel.move(FolderItem.document(draft).payload.rawValue, into: ideas)

        #expect(moved)
        #expect(repository.moves == ["document d1 -> f1"])
        #expect(viewModel.documents.isEmpty)
    }

    @Test func dropsFolderIntoFolder() async {
        let (viewModel, repository) = await makeViewModel()

        #expect(await viewModel.move(FolderItem.folder(work).payload.rawValue, into: ideas))
        #expect(repository.moves == ["folder f2 -> f1"])
        #expect(viewModel.folders.map(\.id) == ["f1"])
    }

    @Test func ignoresDropOnItselfAndForeignText() async {
        let (viewModel, repository) = await makeViewModel()

        #expect(await viewModel.move(FolderItem.folder(ideas).payload.rawValue, into: ideas) == false)
        #expect(await viewModel.move("hello", into: ideas) == false)
        #expect(repository.moves.isEmpty)
    }

    @Test func failedMoveRestoresItemAndShowsError() async {
        let (viewModel, repository) = await makeViewModel()
        repository.moveError = MoveError.folderIntoItself

        #expect(await viewModel.move(FolderItem.folder(work).payload.rawValue, into: ideas) == false)
        #expect(viewModel.folders.map(\.id) == ["f1", "f2"])
        #expect(viewModel.actionError == MoveError.folderIntoItself.userMessage)
    }
}
