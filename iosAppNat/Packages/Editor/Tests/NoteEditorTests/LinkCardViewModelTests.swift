import Foundation
import Testing
@testable import NoteEditor
import Writeopia
import WriteopiaUI
import WrData
import WrModels
import WrNetwork

/// A repository whose backend describes link cards, like the open space.
private final class DescribingRepository: DocumentsRepository, LinkCardDescribing {
    let document: WrDocument
    var description: String? = "A text editor for everyone."
    private(set) var describedSteps: [StoryStep] = []

    init(_ document: WrDocument) { self.document = document }

    func describeLinkCard(documentId: String, step: StoryStep) async throws -> StoryStep {
        describedSteps.append(step)
        guard let description else { throw APIError.unexpectedStatus(502) }
        var described = step
        described.text = description
        return described
    }

    func folderContents(folderId: String) async throws -> FolderContents { FolderContents() }
    func document(id: String) async throws -> WrDocument { document }
    func search(query: String) async throws -> [WrDocument] { [] }
    func createFolder(title: String, parentId: String) async throws -> Folder { throw APIError.notFound }
    func createDocument(title: String, parentId: String) async throws -> WrDocument { throw APIError.notFound }
    func moveDocument(id: String, toFolder folderId: String) async throws {}
    func moveFolder(id: String, toFolder folderId: String) async throws {}
    func save(_ document: WrDocument) async throws {}
    func deleteDocument(id: String) async throws {}
    func duplicate(ids: [String]) async throws {}
    func setFavorite(ids: [String], favorite: Bool) async throws {}
    func deleteItems(ids: [String]) async throws {}
}

private let document = WrDocument(id: "d", title: "Links", workspaceId: "w", content: [
    StoryStep(id: "t", type: .title, text: "Links", position: 0),
    StoryStep(id: "a", type: .text, text: "", position: 1),
])

private func waitForDescription(_ manager: WriteopiaStateManager) async {
    for _ in 0..<200 where !manager.describingLinkStepIds.isEmpty {
        try? await Task.sleep(for: .milliseconds(10))
    }
}

@Suite struct LinkCardViewModelTests {
    @Test func aPastedLinkBecomesACardRightAwayAndGetsItsDescriptionFromTheBackend() async throws {
        let repository = DescribingRepository(document)
        let viewModel = NoteEditorViewModel(documentId: "d", repository: repository)
        await viewModel.loadDocument()
        let manager = viewModel.writeopiaManager

        manager.handleTextInput("https://writeopia.io", cursor: 20, stepId: "a")

        // The card is there before any answer, with the line below it focused.
        let card = try #require(manager.step(withId: "a"))
        #expect(card.type == .linkCard)
        #expect(card.url == "https://writeopia.io")
        #expect(manager.describingLinkStepIds == ["a"])

        await waitForDescription(manager)

        #expect(repository.describedSteps.map(\.id) == ["a"])
        #expect(manager.step(withId: "a")?.text == "A text editor for everyone.")
        #expect(manager.describingLinkStepIds.isEmpty)
    }

    @Test func aFailedDescriptionLeavesTheCardWithoutOne() async throws {
        let repository = DescribingRepository(document)
        repository.description = nil
        let viewModel = NoteEditorViewModel(documentId: "d", repository: repository)
        await viewModel.loadDocument()
        let manager = viewModel.writeopiaManager

        manager.handleTextInput("https://writeopia.io", cursor: 20, stepId: "a")
        await waitForDescription(manager)

        #expect(manager.step(withId: "a")?.type == .linkCard)
        #expect(manager.step(withId: "a")?.text == nil)
        #expect(manager.describingLinkStepIds.isEmpty)
    }

    @Test func withoutABackendTheCardIsNotDescribed() async throws {
        let viewModel = NoteEditorViewModel(documentId: "d", repository: OneDocumentRepository(document))
        await viewModel.loadDocument()
        let manager = viewModel.writeopiaManager

        manager.handleTextInput("https://writeopia.io", cursor: 20, stepId: "a")

        #expect(manager.step(withId: "a")?.type == .linkCard)
        #expect(manager.describingLinkStepIds.isEmpty)
    }
}
