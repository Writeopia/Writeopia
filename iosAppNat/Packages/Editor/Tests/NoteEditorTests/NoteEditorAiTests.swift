import Foundation
import UIKit
import Testing
import Drawing
@testable import NoteEditor
import Writeopia
import WriteopiaUI
import WrData
import WrModels
import WrNetwork

final class FakeAi: AiStreaming {
    var answers: [String] = []
    var error: Error?
    private(set) var requests: [(AiCommand, String)] = []

    func stream(_ command: AiCommand, prompt: String) -> AsyncThrowingStream<String, Error> {
        requests.append((command, prompt))
        let answers = answers
        let error = error
        return AsyncThrowingStream { continuation in
            for answer in answers { continuation.yield(answer) }
            continuation.finish(throwing: error)
        }
    }
}

final class OneDocumentRepository: DocumentsRepository {
    let document: WrDocument
    init(_ document: WrDocument) { self.document = document }

    func folderContents(folderId: String) async throws -> FolderContents { FolderContents() }
    func document(id: String) async throws -> WrDocument { document }
    func search(query: String) async throws -> [WrDocument] { [] }
    func createFolder(title: String, parentId: String) async throws -> Folder { throw APIError.notFound }
    func createDocument(title: String, parentId: String) async throws -> WrDocument { throw APIError.notFound }
    func moveDocument(id: String, toFolder folderId: String) async throws {}
    func moveFolder(id: String, toFolder folderId: String) async throws {}
}

private let document = WrDocument(id: "d", title: "Plan", workspaceId: "w", content: [
    StoryStep(id: "t", type: .title, text: "Plan", position: 0),
    StoryStep(id: "a", type: .text, text: "First idea", position: 1),
    StoryStep(id: "b", type: .text, text: "Second idea", position: 2),
])

private func loadedViewModel(_ ai: FakeAi?) async -> NoteEditorViewModel {
    let viewModel = NoteEditorViewModel(documentId: "d", repository: OneDocumentRepository(document), aiClient: ai)
    await viewModel.loadDocument()
    return viewModel
}

private func waitForAi(_ viewModel: NoteEditorViewModel) async {
    for _ in 0..<100 where viewModel.isAiRunning {
        try? await Task.sleep(for: .milliseconds(10))
    }
}

@Suite struct NoteEditorAiTests {
    @Test func aiIsOnlyAvailableWithAClient() async {
        #expect(await loadedViewModel(nil).isAiAvailable == false)
        #expect(await loadedViewModel(FakeAi()).isAiAvailable)
        #expect(NoteEditorViewModel.commands(for: .cursor) == [.prompt])
        #expect(NoteEditorViewModel.commands(for: .document) == AiCommand.allCases)
    }

    @Test func summaryOfDocumentStreamsIntoAnAnswerAtTheEnd() async {
        let ai = FakeAi()
        ai.answers = ["A short", "A short summary"]
        let viewModel = await loadedViewModel(ai)

        viewModel.runAi(.summary, mode: .document)
        // The loading step shows right away, before any answer.
        #expect(viewModel.writeopiaManager.currentStory.sortedStories.last?.type.number == StoryType.loading.number)
        await waitForAi(viewModel)

        #expect(ai.requests.first?.0 == .summary)
        #expect(ai.requests.first?.1 == "Plan\nFirst idea\nSecond idea")
        let last = viewModel.writeopiaManager.currentStory.sortedStories.last
        #expect(last?.type.number == StoryType.aiAnswer.number)
        #expect(last?.text == "A short summary")
        #expect(viewModel.writeopiaManager.documentContent.count == 4)
    }

    @Test func promptAtCursorAnswersBelowTheFocusedLine() async {
        let ai = FakeAi()
        ai.answers = ["Expanded"]
        let viewModel = await loadedViewModel(ai)
        viewModel.writeopiaManager.onFocusChange(stepId: "a", hasFocus: true)

        viewModel.runAi(.prompt, mode: .cursor)
        await waitForAi(viewModel)

        #expect(ai.requests.first?.1 == "First idea")
        let texts = viewModel.writeopiaManager.currentStory.sortedStories.map { $0.text ?? "" }
        #expect(texts == ["Plan", "First idea", "Expanded", "Second idea"])
    }

    @Test func errorsAreShownInTheAnswer() async {
        let ai = FakeAi()
        ai.error = AiStreamError(message: "Quota exceeded")
        let viewModel = await loadedViewModel(ai)

        viewModel.runAi(.faq, mode: .document)
        await waitForAi(viewModel)

        let last = viewModel.writeopiaManager.currentStory.sortedStories.last
        #expect(last?.type.number == StoryType.aiAnswer.number)
        #expect(last?.text == "Error. Message: Quota exceeded")
    }

    @Test func emptyAnswerRemovesTheLoadingStep() async {
        let viewModel = await loadedViewModel(FakeAi())

        viewModel.runAi(.tags, mode: .document)
        await waitForAi(viewModel)

        #expect(viewModel.writeopiaManager.currentStory.stories.count == 3)
    }
}

@Suite struct LinkInputTests {
    @Test func addsSchemeWhenMissing() {
        #expect(NoteEditorView.normalizedURL(" writeopia.io ") == "https://writeopia.io")
        #expect(NoteEditorView.normalizedURL("http://x.io") == "http://x.io")
        #expect(NoteEditorView.normalizedURL("   ") == nil)
    }

    @Test func linkIsAppliedToTheSavedSelection() async {
        let viewModel = await loadedViewModel(nil)
        let manager = viewModel.writeopiaManager
        manager.onSelectionChange(stepId: "a", start: 0, end: 5)
        let selection = manager.textSelection!

        // The alert takes the focus away, which clears the live selection.
        manager.onFocusChange(stepId: "a", hasFocus: false)
        manager.setLink("https://writeopia.io", for: selection)

        #expect(manager.step(withId: "a")?.spans == [SpanInfo(start: 0, end: 5, span: "LINK", extra: "https://writeopia.io")])
    }
}

final class FakePublishing: DocumentPublishing {
    var published = false
    var error: Error?

    func isPublished(documentId: String) async throws -> Bool { published }

    func publish(documentId: String) async throws {
        if let error { throw error }
        published = true
    }

    func unpublish(documentId: String) async throws {
        if let error { throw error }
        published = false
    }
}

@Suite struct NoteMenuTests {
    private func viewModel(
        publishing: DocumentPublishing? = nil,
        isPremium: Bool = false,
        defaults: UserDefaults = UserDefaults(suiteName: "menu.\(UUID().uuidString)")!
    ) async -> NoteEditorViewModel {
        let viewModel = NoteEditorViewModel(
            documentId: "d",
            repository: OneDocumentRepository(document),
            publishing: publishing,
            isPremium: isPremium,
            defaults: defaults
        )
        await viewModel.loadDocument()
        return viewModel
    }

    @Test func lockStopsEditing() async {
        let viewModel = await viewModel()
        viewModel.writeopiaManager.onSelected(stepId: "a", isSelected: true)

        viewModel.toggleLock()
        viewModel.writeopiaManager.handleTextInput("Changed", cursor: 7, stepId: "a")

        #expect(viewModel.isLocked)
        #expect(viewModel.writeopiaManager.step(withId: "a")?.text == "First idea")
        #expect(!viewModel.writeopiaManager.hasSelectedLines)

        viewModel.toggleLock()
        #expect(!viewModel.isLocked)
    }

    @Test func fontIsRememberedForNextDocuments() async {
        let defaults = UserDefaults(suiteName: "font.\(UUID().uuidString)")!
        let first = await viewModel(defaults: defaults)

        first.changeFontFamily(.serif)

        #expect(first.writeopiaManager.fontFamily == .serif)
        #expect(await viewModel(defaults: defaults).fontFamily == .serif)
    }

    @Test func jsonExportWrapsTheCurrentDocumentInData() async throws {
        let viewModel = await viewModel()
        viewModel.writeopiaManager.handleTextInput("First idea!", cursor: 11, stepId: "a")

        let json = try JSONSerialization.jsonObject(with: Data(try viewModel.exportJson().utf8)) as! [String: Any]
        let data = json["data"] as! [String: Any]
        let content = data["content"] as! [[String: Any]]

        #expect(data["id"] as? String == "d")
        #expect(data["title"] as? String == "Plan")
        #expect(content.map { $0["text"] as? String } == ["Plan", "First idea!", "Second idea"])
    }

    @Test func exportFilesAreNamedAfterTheTitle() async throws {
        let viewModel = await viewModel()

        let url = try viewModel.exportFile(.markdown)

        #expect(url.lastPathComponent == "Plan.md")
        #expect(try String(contentsOf: url, encoding: .utf8) == "# Plan\nFirst idea\nSecond idea\n")
        #expect(NoteEditorViewModel.fileName(for: "My plan: v2") == "My_plan_v2")
        #expect(NoteEditorViewModel.fileName(for: "  ") == "Untitled")
    }

    @Test func publishingNeedsPremiumAndABackend() async {
        #expect(await viewModel().canPublish == false)
        #expect(await viewModel(publishing: FakePublishing(), isPremium: false).canPublish == false)
        #expect(await viewModel(publishing: FakePublishing(), isPremium: true).canPublish)
    }

    @Test func publishAndUnpublish() async {
        let publishing = FakePublishing()
        let viewModel = await viewModel(publishing: publishing, isPremium: true)

        await viewModel.setPublished(true)
        #expect(viewModel.isPublished)
        #expect(publishing.published)
        #expect(viewModel.siteURL.absoluteString == "https://app.writeopia.io/site/d")

        await viewModel.setPublished(false)
        #expect(!viewModel.isPublished)
    }

    @Test func publishErrorsAreShown() async {
        let publishing = FakePublishing()
        publishing.error = APIError.forbidden(nil)
        let viewModel = await viewModel(publishing: publishing, isPremium: true)

        await viewModel.setPublished(true)

        #expect(!viewModel.isPublished)
        #expect(viewModel.publishError != nil)
    }
}

@Suite struct DrawingStepTests {
    @Test func newDrawingGoesToTheEndAndIsDrawn() async throws {
        let viewModel = await loadedViewModel(nil)
        let drawing = DrawingData(id: "x", strokes: [Stroke(points: [DrawPoint(x: 1, y: 1)])])

        viewModel.saveDrawing(drawing, stepId: nil)

        let last = try #require(viewModel.writeopiaManager.currentStory.sortedStories.last)
        #expect(last.type.number == StoryType.drawing.number)
        #expect(DrawingData.fromJson(last.text) == drawing)
        #expect(viewModel.writeopiaManager.toDraw.contains { $0.id == last.id })
        #expect(viewModel.writeopiaManager.documentContent.count == 4)
    }

    @Test func editedDrawingReplacesItsStrokes() async throws {
        let viewModel = await loadedViewModel(nil)
        viewModel.saveDrawing(DrawingData(id: "x", strokes: [Stroke(id: "a", points: [DrawPoint(x: 1, y: 1)])]), stepId: nil)
        let stepId = try #require(viewModel.writeopiaManager.currentStory.sortedStories.last?.id)

        viewModel.saveDrawing(DrawingData(id: "x", strokes: [Stroke(id: "b", points: [DrawPoint(x: 2, y: 2)])]), stepId: stepId)

        let step = try #require(viewModel.writeopiaManager.step(withId: stepId))
        #expect(DrawingData.fromJson(step.text)?.strokes.map(\.id) == ["b"])
        #expect(viewModel.writeopiaManager.currentStory.stories.count == 4)
    }

    @Test func emptyNewDrawingIsDropped() async {
        let viewModel = await loadedViewModel(nil)

        viewModel.saveDrawing(DrawingData(), stepId: nil)

        #expect(viewModel.writeopiaManager.currentStory.stories.count == 3)
    }

    @Test func drawingsStayOutOfMarkdownAndPreviews() async {
        let viewModel = await loadedViewModel(nil)
        viewModel.saveDrawing(DrawingData(strokes: [Stroke(points: [DrawPoint(x: 1, y: 1)])]), stepId: nil)

        #expect(!viewModel.exportMarkdown().contains("strokes"))
        #expect(!viewModel.currentDocument.preview.contains("strokes"))
    }
}

final class FakeClipboard: LineClipboard {
    private(set) var copied: [StoryStep] = []
    func copy(_ lines: [StoryStep]) { copied = lines }
}

final class CreatingRepository: DocumentsRepository {
    private(set) var created: [(String, String)] = []

    func folderContents(folderId: String) async throws -> FolderContents { FolderContents() }
    func document(id: String) async throws -> WrDocument {
        WrDocument(id: "d", title: "Plan", workspaceId: "w", content: NoteEditorTests.document.content, parentId: "folder-1")
    }
    func search(query: String) async throws -> [WrDocument] { [] }
    func createFolder(title: String, parentId: String) async throws -> Folder { throw APIError.notFound }
    func createDocument(title: String, parentId: String) async throws -> WrDocument {
        created.append((title, parentId))
        return WrDocument(id: "new-page", title: title, workspaceId: "w", parentId: parentId)
    }
    func moveDocument(id: String, toFolder folderId: String) async throws {}
    func moveFolder(id: String, toFolder folderId: String) async throws {}
}

@Suite struct SelectionMenuTests {
    @Test func copyAndCutSelectedLines() async {
        let viewModel = await loadedViewModel(nil)
        let clipboard = FakeClipboard()
        viewModel.writeopiaManager.onSelected(stepId: "a", isSelected: true)
        viewModel.writeopiaManager.onSelected(stepId: "b", isSelected: true)

        viewModel.copySelectedLines(to: clipboard)
        #expect(clipboard.copied.map(\.id) == ["a", "b"])
        #expect(viewModel.writeopiaManager.hasSelectedLines)

        viewModel.cutSelectedLines(to: clipboard)
        #expect(viewModel.writeopiaManager.currentStory.sortedStories.map(\.id) == ["t"])
        #expect(!viewModel.writeopiaManager.hasSelectedLines)
    }

    @Test func linkToPageCreatesADocumentAndLinksIt() async {
        let repository = CreatingRepository()
        let viewModel = NoteEditorViewModel(documentId: "d", repository: repository)
        await viewModel.loadDocument()
        viewModel.writeopiaManager.onSelected(stepId: "a", isSelected: true)

        await viewModel.linkSelectionToNewPage()

        #expect(repository.created.first?.0 == "First idea")
        #expect(repository.created.first?.1 == "folder-1")
        let link = viewModel.writeopiaManager.currentStory.sortedStories[2]
        #expect(link.documentLink == DocumentLink(id: "new-page", title: "First idea"))
    }

    @Test func aiOnSelectedLinesAnswersAfterTheLastOne() async {
        let ai = FakeAi()
        ai.answers = ["Summary"]
        let viewModel = await loadedViewModel(ai)
        viewModel.writeopiaManager.onSelected(stepId: "a", isSelected: true)

        viewModel.runAi(.summary, mode: .selectedLines)
        await waitForAi(viewModel)

        #expect(ai.requests.first?.1 == "First idea")
        #expect(viewModel.writeopiaManager.currentStory.sortedStories.map { $0.text ?? "" } == ["Plan", "First idea", "Summary", "Second idea"])
        #expect(NoteEditorViewModel.commands(for: .selectedLines) == AiCommand.allCases)
        #expect(!AiTargetMode.pickable.contains(.selectedLines))
    }
}

final class FakeUploader: ImageUploading {
    var url: String? = "https://cdn.writeopia.io/img.jpg"
    private(set) var uploads: [(Int, String, String)] = []

    func uploadImage(_ data: Data, fileName: String, mimeType: String) async throws -> String {
        uploads.append((data.count, fileName, mimeType))
        guard let url else { throw APIError.unexpectedStatus(500) }
        return url
    }
}

private func pngData(width: CGFloat, height: CGFloat) -> Data {
    UIGraphicsImageRenderer(size: CGSize(width: width, height: height)).pngData { context in
        UIColor.systemPink.setFill()
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
    }
}

@Suite struct ImageFeatureTests {
    private func viewModel(uploader: ImageUploading?) async -> NoteEditorViewModel {
        let viewModel = NoteEditorViewModel(documentId: "d", repository: OneDocumentRepository(document), imageUploader: uploader)
        await viewModel.loadDocument()
        return viewModel
    }

    @Test func picturesBecomeJpegsNoLargerThan2048() throws {
        let jpeg = try #require(ImageProcessing.jpeg(from: pngData(width: 4000, height: 1000)))
        let image = try #require(UIImage(data: jpeg))

        #expect(image.size == CGSize(width: 2048, height: 512))
        #expect(jpeg.starts(with: [0xFF, 0xD8]))
        #expect(ImageProcessing.jpeg(from: Data("not an image".utf8)) == nil)
    }

    @Test func openSpaceUploadsAndUsesTheUrl() async throws {
        let uploader = FakeUploader()
        let viewModel = await viewModel(uploader: uploader)

        await viewModel.addImage(pngData(width: 100, height: 100))

        let image = try #require(viewModel.writeopiaManager.currentStory.sortedStories.last)
        #expect(image.type.number == StoryType.image.number)
        #expect(image.url == "https://cdn.writeopia.io/img.jpg")
        #expect(image.path == nil)
        #expect(uploader.uploads.first?.2 == "image/jpeg")
        #expect(uploader.uploads.first?.1.hasSuffix(".jpg") == true)
    }

    @Test func privateSpaceKeepsTheImageOnTheDevice() async throws {
        let viewModel = await viewModel(uploader: nil)

        await viewModel.addImage(pngData(width: 100, height: 100))

        let image = try #require(viewModel.writeopiaManager.currentStory.sortedStories.last)
        let path = try #require(image.path)
        #expect(FileManager.default.fileExists(atPath: path))
        #expect(image.url == nil)
        #expect(viewModel.writeopiaManager.uploadingStepIds.isEmpty)
    }

    @Test func failedUploadFallsBackToTheLocalFile() async throws {
        let uploader = FakeUploader()
        uploader.url = nil
        let viewModel = await viewModel(uploader: uploader)

        await viewModel.addImage(pngData(width: 50, height: 50))

        let image = try #require(viewModel.writeopiaManager.currentStory.sortedStories.last)
        #expect(image.path != nil)
        #expect(image.url == nil)
    }

    @Test func invalidDataShowsAnError() async {
        let viewModel = await viewModel(uploader: nil)

        await viewModel.addImage(Data("nope".utf8))

        #expect(viewModel.imageError != nil)
        #expect(viewModel.writeopiaManager.currentStory.stories.count == 3)
    }
}
