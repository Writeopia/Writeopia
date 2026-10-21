import Foundation
import Testing
@testable import NoteEditor
import Writeopia
import WriteopiaUI
import WrData
import WrModels
import WrNetwork

private final class FakeWritingAgentAPI: WritingAgentServing {
    var answer: [WritingSuggestionDto] = []
    var folderAnswer: [FolderSuggestionDto] = []
    var folderFailure: Error?
    private(set) var requests: [WritingSuggestionsRequest] = []
    private(set) var folderRequests: [FolderSuggestionsRequest] = []

    func suggestions(_ request: WritingSuggestionsRequest) async throws -> WritingSuggestionsResponse {
        requests.append(request)
        return WritingSuggestionsResponse(suggestions: answer)
    }

    func folderSuggestions(_ request: FolderSuggestionsRequest) async throws -> FolderSuggestionsResponse {
        folderRequests.append(request)
        if let folderFailure { throw folderFailure }
        return FolderSuggestionsResponse(suggestions: folderAnswer)
    }

    var tagAnswer: [TagSuggestionDto] = []
    private(set) var tagRequests: [TagSuggestionsRequest] = []
    private(set) var createTagRequests: [TagCreateRequest] = []

    func tagSuggestions(_ request: TagSuggestionsRequest) async throws -> TagSuggestionsResponse {
        tagRequests.append(request)
        return TagSuggestionsResponse(suggestions: tagAnswer)
    }

    func createTag(_ request: TagCreateRequest) async throws -> TagCreateResponse {
        createTagRequests.append(request)
        return TagCreateResponse(name: "Fresh", description: "Made by the AI")
    }
}

private final class FakeTags: DocumentTags {
    var current: [String] = []
    var catalogue = (1...35).map { TagCandidate(id: "t\($0)", name: "Tag \($0)", description: "About \($0)") }
    private(set) var added: [[String]] = []
    private(set) var created: [(String, String)] = []

    func currentTagIds() -> [String] { current }
    func candidates(limit: Int) async throws -> [TagCandidate] { Array(catalogue.prefix(limit)) }
    func addTags(_ tagIds: [String]) async throws {
        added.append(tagIds)
        current += tagIds
    }
    func createTagWithAi(documentTitle: String, documentText: String) async throws -> DocumentTag {
        created.append((documentTitle, documentText))
        current.append("fresh")
        return DocumentTag(id: "fresh", workspaceId: "w", name: "Fresh", description: "Made by the AI")
    }
}

private final class FakeFolders: DocumentFolders {
    var currentFolder = Folder.rootId
    var folders = (1...12).map { FolderCandidate(id: "f\($0)", name: "Folder \($0)") }
    private(set) var movedTo: [String] = []
    private(set) var created: [(String, String)] = []

    func currentFolderId() -> String { currentFolder }
    func candidates(limit: Int) async throws -> [FolderCandidate] { Array(folders.prefix(limit)) }
    func moveTo(folderId: String) async throws { movedTo.append(folderId) }
    func createFolderAndMove(documentTitle: String, documentText: String) async throws -> String {
        created.append((documentTitle, documentText))
        return "New folder"
    }
}

private final class FakeImages: AiImageGenerating {
    var url = "https://images.test/milk.png"
    var failure: Error?
    private(set) var requests: [(String, String, String)] = []
    /// What the document looked like while the picture was drawn.
    var sawTypes: (() -> [Int])?
    private(set) var typesWhileDrawing: [Int] = []

    func generateImage(paragraph: String, documentTitle: String, documentText: String) async throws -> String {
        requests.append((paragraph, documentTitle, documentText))
        typesWhileDrawing = sawTypes?() ?? []
        if let failure { throw failure }
        return url
    }
}

private func manager(title: String = "Groceries", paragraphs: [String] = ["We need milk.", "And eggs."]) -> WriteopiaStateManager {
    let manager = WriteopiaStateManager()
    var steps = [StoryStep(id: "title", type: .title, text: title, position: 0)]
    for (index, text) in paragraphs.enumerated() {
        steps.append(StoryStep(id: "p\(index + 1)", type: .text, text: text, position: Int(index + 1)))
    }
    manager.loadDocument(WrDocument(id: "doc", title: title, workspaceId: "w", content: steps))
    return manager
}

private func executor(_ manager: WriteopiaStateManager, ai: FakeAi?, images: FakeImages? = nil) -> WritingActionExecutor {
    WritingActionExecutor(
        manager: manager,
        resolveAi: { ai },
        resolveImageAi: { images },
        createDocument: { title in ("new-doc", title) }
    )
}

private func controller(
    api: FakeWritingAgentAPI,
    manager: WriteopiaStateManager,
    folders: FakeFolders? = nil,
    tags: FakeTags? = nil,
    enabled: Bool = true
) -> WritingAgentController {
    WritingAgentController(
        api: api,
        manager: manager,
        executor: executor(manager, ai: nil),
        folders: folders,
        tags: tags,
        isEnabled: { enabled },
        debounce: .milliseconds(20),
        minimumDocumentLength: 40,
        folderMinimumDocumentLength: 30,
        tagMinimumDocumentLength: 30
    )
}

/// Waits for the controller's tasks to run; the test host may be busy with other suites.
private func settle(until condition: @escaping () -> Bool = { false }) async {
    for _ in 0..<200 {
        try? await Task.sleep(for: .milliseconds(10))
        if condition() { return }
    }
}

private func types(_ manager: WriteopiaStateManager) -> [Int] {
    manager.currentStory.sortedStories.map(\.type.number)
}

private func texts(_ manager: WriteopiaStateManager) -> [String] {
    manager.currentStory.sortedStories.map { $0.text ?? "" }
}

/// Puts the cursor in the step with `id`, like the text view does.
private func focus(_ manager: WriteopiaStateManager, _ id: String) {
    manager.onFocusChange(stepId: id, hasFocus: true)
}

private func type(_ manager: WriteopiaStateManager, _ id: String, _ text: String) {
    focus(manager, id)
    manager.handleTextInput(text, cursor: text.count, stepId: id)
}

@Suite struct WritingAgentExecutorTests {
    @Test func codeSnippetGoesBelowTheParagraphOneCodeBlockPerLine() async {
        let manager = manager()
        let ai = FakeAi()
        ai.answers = ["Here it is:\n```kotlin\nfun main() {\n    println(\"hi\")\n}\n```\nDone."]

        await executor(manager, ai: ai).execute(.codeBlock, stepId: "p1", ui: NoWritingAgentUI())

        #expect(types(manager) == [StoryType.title.number, StoryType.text.number, StoryType.codeBlock.number, StoryType.codeBlock.number, StoryType.codeBlock.number, StoryType.text.number])
        #expect(texts(manager) == ["Groceries", "We need milk.", "fun main() {", "    println(\"hi\")", "}", "And eggs."])
    }

    @Test func listItemsFromTheAiGoBelowTheParagraph() async {
        let manager = manager()
        let ai = FakeAi()
        ai.answers = ["- Milk\n- Eggs\n- Bread"]

        await executor(manager, ai: ai).execute(.checkList, stepId: "p1", ui: NoWritingAgentUI())

        #expect(types(manager) == [StoryType.title.number, StoryType.text.number, StoryType.checkItem.number, StoryType.checkItem.number, StoryType.checkItem.number, StoryType.text.number])
        #expect(Array(texts(manager)[2...4]) == ["Milk", "Eggs", "Bread"])
        #expect(manager.currentStory.sortedStories[2].checked == false)
    }

    @Test func withoutAnAiTheFailureIsKeptAndNothingChanges() async {
        let manager = manager()
        let executor = executor(manager, ai: nil)

        await executor.execute(.list, stepId: "p1", ui: NoWritingAgentUI())

        #expect(types(manager) == [StoryType.title.number, StoryType.text.number, StoryType.text.number])
        #expect(executor.error == WritingAgentError.noAi.message)
        #expect(executor.activity == nil)
    }

    @Test func calloutHighlightsTheParagraph() async {
        let manager = manager()

        await executor(manager, ai: nil).execute(.callout, stepId: "p1", ui: NoWritingAgentUI())

        #expect(manager.step(withId: "p1")?.hasTag(BlockTag.box.rawValue) == true)
    }

    @Test func headingGoesAboveTheParagraph() async {
        let manager = manager()
        let ai = FakeAi()
        ai.answers = ["## \"Shopping\".\n"]

        await executor(manager, ai: ai).execute(.sectionHeading, stepId: "p2", ui: NoWritingAgentUI())

        #expect(texts(manager) == ["Groceries", "We need milk.", "Shopping", "And eggs."])
        #expect(manager.currentStory.sortedStories[2].hasTag("H2"))
    }

    @Test func documentTitleIsWrittenIntoTheTitleStep() async {
        let manager = manager(title: "")
        let ai = FakeAi()
        ai.answers = ["Weekly Groceries"]

        await executor(manager, ai: ai).execute(.documentTitle, stepId: nil, ui: NoWritingAgentUI())

        #expect(manager.title == "Weekly Groceries")
    }

    @Test func tldrIsACardUnderTheTitleAndConclusionAnAnswerAtTheEnd() async {
        let manager = manager()
        let ai = FakeAi()
        ai.answers = ["Short", "Short version."]
        let executor = executor(manager, ai: ai)

        await executor.execute(.tldr, stepId: "title", ui: NoWritingAgentUI())
        await executor.execute(.conclusion, stepId: nil, ui: NoWritingAgentUI())

        let steps = manager.currentStory.sortedStories
        #expect(steps[1].text == "TLDR: Short version.")
        #expect(steps[1].hasTag(BlockTag.card.rawValue))
        #expect(steps.last?.type.number == StoryType.aiAnswer.number)
        #expect(steps.last?.text == "Short version.")
    }

    @Test func imageDrawnByTheCloudReplacesALoadingBlockBelowTheParagraph() async {
        let manager = manager()
        let images = FakeImages()
        images.sawTypes = { types(manager) }

        await executor(manager, ai: nil, images: images).execute(.image, stepId: "p1", ui: NoWritingAgentUI())

        #expect(images.requests.count == 1)
        #expect(images.requests.first?.0 == "We need milk.")
        #expect(images.requests.first?.1 == "Groceries")
        #expect(images.requests.first?.2.contains("And eggs.") == true)
        #expect(images.typesWhileDrawing == [StoryType.title.number, StoryType.text.number, StoryType.loading.number, StoryType.text.number])
        #expect(types(manager) == [StoryType.title.number, StoryType.text.number, StoryType.image.number, StoryType.text.number])
        #expect(manager.currentStory.sortedStories[2].url == "https://images.test/milk.png")
    }

    @Test func whenTheCloudFailsToDrawTheLoadingBlockGoesAway() async {
        let manager = manager()
        let images = FakeImages()
        images.failure = AiStreamError(message: "quota")
        let executor = executor(manager, ai: nil, images: images)

        await executor.execute(.image, stepId: "p1", ui: NoWritingAgentUI())

        #expect(types(manager) == [StoryType.title.number, StoryType.text.number, StoryType.text.number])
        #expect(executor.error == "quota")
    }

    @Test func withoutTheCloudTheImagePickerOpens() async {
        final class PickerUI: WritingAgentUI {
            var picked = 0
            func pickImage() { picked += 1 }
            func openDrawing() {}
        }
        let ui = PickerUI()

        await executor(manager(), ai: nil).execute(.image, stepId: "p1", ui: ui)

        #expect(ui.picked == 1)
    }

    @Test func linkedDocumentIsNamedAfterTheParagraph() async {
        let manager = manager()

        await executor(manager, ai: nil).execute(.newDocumentLink, stepId: "p1", ui: NoWritingAgentUI())

        let link = manager.currentStory.sortedStories[2]
        #expect(link.type.number == StoryType.documentLink.number)
        #expect(link.documentLink?.id == "new-doc")
        #expect(link.text == "We need milk.")
    }
}

@Suite struct WritingAgentControllerTests {
    @Test func aNewSentenceAsksAboutTheFocusedParagraph() async {
        let api = FakeWritingAgentAPI()
        api.answer = [WritingSuggestionDto(action: .list, probability: 0.9), WritingSuggestionDto(action: .spreadsheet, probability: 0.95)]
        let manager = manager(paragraphs: ["We need milk"])
        let controller = controller(api: api, manager: manager)
        controller.documentOpened(id: "doc")

        type(manager, "p1", "We need milk")
        controller.storyChanged()
        type(manager, "p1", "We need milk.")
        controller.storyChanged()
        await settle { !controller.state.suggestions.isEmpty }

        #expect(api.requests.count == 1)
        #expect(api.requests.first?.scope == .writing)
        #expect(api.requests.first?.text == "We need milk.")
        #expect(api.requests.first?.blockType == "TEXT")
        // Spreadsheets aren't drawn by this editor, so they are never offered.
        #expect(controller.state.suggestions.map(\.action) == [.list])
        #expect(controller.state.stepId == "p1")
        controller.stop()
    }

    @Test func typingWithoutEndingASentenceAsksNothing() async {
        let api = FakeWritingAgentAPI()
        let manager = manager(paragraphs: ["We need milk"])
        let controller = controller(api: api, manager: manager)
        controller.documentOpened(id: "doc")

        type(manager, "p1", "We need milk")
        controller.storyChanged()
        type(manager, "p1", "We need milk and")
        controller.storyChanged()
        await settle()

        #expect(api.requests.isEmpty)
        controller.stop()
    }

    @Test func anOpenedDocumentIsAskedAboutOnceAnchoredToItsTitle() async {
        let api = FakeWritingAgentAPI()
        api.answer = [WritingSuggestionDto(action: .tldr, probability: 0.95)]
        let manager = manager(paragraphs: [String(repeating: "A", count: 60)])
        let controller = controller(api: api, manager: manager)

        controller.documentOpened(id: "doc")
        controller.documentOpened(id: "doc")
        await settle { !controller.state.suggestions.isEmpty }

        #expect(api.requests.count == 1)
        #expect(api.requests.first?.scope == .documentOpened)
        #expect(controller.state.stepId == "title")
        #expect(controller.state.suggestions.map(\.action) == [.tldr])
        controller.stop()
    }

    @Test func anUntitledDocumentWithASentenceIsOfferedATitleFirst() async {
        let api = FakeWritingAgentAPI()
        let manager = manager(title: "", paragraphs: ["We need milk."])
        let controller = controller(api: api, manager: manager)

        controller.documentOpened(id: "doc")

        #expect(controller.state.suggestions.map(\.action) == [.documentTitle])
        #expect(api.requests.isEmpty)

        type(manager, "title", "Groceries")
        controller.storyChanged()
        #expect(controller.state.suggestions.isEmpty)
        controller.stop()
    }

    @Test func applyingASuggestionRunsItOnTheRememberedParagraphAndDropsIt() async {
        let api = FakeWritingAgentAPI()
        api.answer = [WritingSuggestionDto(action: .callout, probability: 0.9)]
        let manager = manager(paragraphs: ["We need milk"])
        let controller = controller(api: api, manager: manager)
        controller.documentOpened(id: "doc")
        type(manager, "p1", "We need milk")
        controller.storyChanged()
        type(manager, "p1", "We need milk.")
        controller.storyChanged()
        await settle { !controller.state.suggestions.isEmpty }

        controller.execute(.callout)
        await settle { manager.step(withId: "p1")?.hasTag(BlockTag.box.rawValue) == true }

        #expect(controller.state.suggestions.isEmpty)
        #expect(manager.step(withId: "p1")?.hasTag(BlockTag.box.rawValue) == true)
        controller.stop()
    }

    @Test func aRootDocumentIsOfferedAFolderInTheBoxWithoutAskingTheBackend() async {
        let api = FakeWritingAgentAPI()
        let controller = controller(api: api, manager: manager(paragraphs: ["We need milk, eggs and bread for the week."]), folders: FakeFolders())

        controller.documentOpened(id: "doc")

        #expect(controller.state.offerFolder)
        #expect(controller.state.hasSuggestions)
        #expect(controller.state.folderDialog == nil)
        #expect(api.folderRequests.isEmpty)
        controller.stop()
    }

    @Test func aDocumentInAFolderIsNotOfferedOne() async {
        let folders = FakeFolders()
        folders.currentFolder = "f3"
        let controller = controller(api: FakeWritingAgentAPI(), manager: manager(paragraphs: ["We need milk, eggs and bread for the week."]), folders: folders)

        controller.documentOpened(id: "doc")

        #expect(controller.state.offerFolder == false)
        controller.stop()
    }

    @Test func takingTheOfferOpensADialogThatAsksAboutTenFoldersAndListsTheFits() async {
        let api = FakeWritingAgentAPI()
        api.folderAnswer = [FolderSuggestionDto(folderId: "f2", probability: 0.95), FolderSuggestionDto(folderId: "f1", probability: 0.9)]
        let folders = FakeFolders()
        let controller = controller(api: api, manager: manager(paragraphs: ["We need milk, eggs and bread for the week."]), folders: folders)
        controller.documentOpened(id: "doc")

        controller.openFolderDialog()
        #expect(controller.state.folderDialog?.isLoading == true)
        await settle { controller.state.folderDialog?.isLoading == false }

        #expect(api.folderRequests.count == 1)
        #expect(api.folderRequests.first?.folders.count == 10)
        #expect(api.folderRequests.first?.documentTitle == "Groceries")
        #expect(controller.state.folderDialog?.isLoading == false)
        #expect(controller.state.folderDialog?.options.map(\.name) == ["Folder 2", "Folder 1"])

        controller.moveToFolder("f2")
        await settle { !folders.movedTo.isEmpty }
        #expect(folders.movedTo == ["f2"])
        #expect(controller.state.folderDialog == nil)
        #expect(controller.state.offerFolder == false)
        controller.stop()
    }

    @Test func whenTheBackendCannotAnswerTheDialogOnlyOffersANewFolder() async {
        let api = FakeWritingAgentAPI()
        api.folderFailure = APIError.notFound
        let folders = FakeFolders()
        let controller = controller(api: api, manager: manager(paragraphs: ["We need milk, eggs and bread for the week."]), folders: folders)
        controller.documentOpened(id: "doc")

        controller.openFolderDialog()
        await settle { controller.state.folderDialog?.isLoading == false }

        #expect(controller.state.folderDialog?.options.isEmpty == true)
        #expect(controller.state.folderDialog?.isLoading == false)

        controller.createFolder()
        #expect(controller.state.folderDialog?.isCreating == true)
        await settle { controller.state.folderDialog == nil }
        #expect(folders.created.count == 1)
        #expect(folders.created.first?.0 == "Groceries")
        #expect(controller.state.folderDialog == nil)
        #expect(controller.state.offerFolder == false)
        controller.stop()
    }

    @Test func aDocumentWithoutTagsIsOfferedSomeWithoutAskingTheBackend() async {
        let api = FakeWritingAgentAPI()
        let controller = controller(api: api, manager: manager(paragraphs: ["We need milk, eggs and bread for the week."]), tags: FakeTags())
        controller.documentOpened(id: "doc")

        #expect(api.tagRequests.isEmpty)
        #expect(controller.state.offerTags)
        #expect(controller.state.hasSuggestions)
        #expect(controller.state.tagDialog == nil)
        controller.stop()
    }

    @Test func aDocumentThatAlreadyHasTagsIsNotOfferedAny() async {
        let tags = FakeTags()
        tags.current = ["t1"]
        let controller = controller(api: FakeWritingAgentAPI(), manager: manager(paragraphs: ["We need milk, eggs and bread for the week."]), tags: tags)
        controller.documentOpened(id: "doc")

        #expect(controller.state.offerTags == false)
        controller.stop()
    }

    @Test func takingTheTagOfferAsksAboutThirtyTagsAndAddsTheSelectedOnes() async {
        let api = FakeWritingAgentAPI()
        api.tagAnswer = [TagSuggestionDto(tagId: "t2", probability: 0.95), TagSuggestionDto(tagId: "t1", probability: 0.9)]
        let tags = FakeTags()
        let controller = controller(api: api, manager: manager(paragraphs: ["We need milk, eggs and bread for the week."]), tags: tags)
        controller.documentOpened(id: "doc")

        controller.openTagDialog()
        #expect(controller.state.tagDialog?.isLoading == true)
        await settle { controller.state.tagDialog?.isLoading == false }

        #expect(api.tagRequests.count == 1)
        #expect(api.tagRequests.first?.tags.count == TagSuggestionsRequest.maxTags)
        #expect(api.tagRequests.first?.tags.first?.description == "About 1")
        #expect(controller.state.tagDialog?.options.map(\.name) == ["Tag 2", "Tag 1"])
        #expect(controller.state.tagDialog?.selected == ["t2", "t1"])

        controller.toggleTag("t1")
        #expect(controller.state.tagDialog?.selected == ["t2"])
        controller.applyTags()
        await settle { !tags.added.isEmpty }

        #expect(tags.added == [["t2"]])
        #expect(controller.state.tagDialog == nil)
        #expect(controller.state.offerTags == false)
        controller.stop()
    }

    @Test func creatingATagWithTheAiSpinsAndWithdrawsTheOffer() async {
        let tags = FakeTags()
        tags.catalogue = []
        let controller = controller(api: FakeWritingAgentAPI(), manager: manager(paragraphs: ["We need milk, eggs and bread for the week."]), tags: tags)
        controller.documentOpened(id: "doc")

        controller.openTagDialog()
        await settle { controller.state.tagDialog?.isLoading == false }
        #expect(controller.state.tagDialog?.catalogueIsEmpty == true)

        controller.createTagWithAi()
        #expect(controller.state.tagDialog?.isCreating == true)
        await settle { controller.state.tagDialog == nil }

        #expect(tags.created.count == 1)
        #expect(tags.created.first?.0 == "Groceries")
        #expect(controller.state.offerTags == false)
        controller.stop()
    }

    @Test func notNowKeepsTheOfferAndDismissingTheBoxWithdrawsIt() async {
        let api = FakeWritingAgentAPI()
        let controller = controller(api: api, manager: manager(paragraphs: ["We need milk, eggs and bread for the week."]), folders: FakeFolders())
        controller.documentOpened(id: "doc")

        controller.openFolderDialog()
        await settle { controller.state.folderDialog?.isLoading == false }
        controller.dismissFolderDialog()
        #expect(controller.state.folderDialog == nil)
        #expect(controller.state.offerFolder)

        controller.dismiss()
        #expect(controller.state.hasSuggestions == false)
        controller.stop()
    }

    @Test func theBoxFoldsToABadgeThatCountsTheRows() async {
        let api = FakeWritingAgentAPI()
        let controller = controller(api: api, manager: manager(title: "", paragraphs: ["We need milk, eggs and bread for the week."]), folders: FakeFolders())
        controller.documentOpened(id: "doc")

        #expect(controller.collapsed == false)
        controller.toggleCollapsed()
        #expect(controller.collapsed)
        // The title offer and the folder offer: two rows behind the badge.
        #expect(controller.state.count == 2)
        controller.stop()
    }
}

@Suite struct WritingAgentHelpersTests {
    @Test func sentenceEndsCountsPeriodsExclamationsAndQuestions() {
        #expect(SentenceEnds.count("Hi. Really?! Yes") == 3)
        #expect(SentenceEnds.count("The ingredients are:") == 1)
        #expect(SentenceEnds.count("") == 0)
    }

    @Test func markdownCodeTakesTheFirstFencedBlock() {
        #expect(MarkdownCode.parse("text\n```swift\nlet a = 1\n```\nmore") == "let a = 1")
        #expect(MarkdownCode.parse("plain code") == "plain code")
        // Backticks inside the code, or a shorter fence, don't close the block; a longer one does.
        #expect(MarkdownCode.parse("```sh\necho \"```x```\"\n```x\nstill\n````  \nafter") == "echo \"```x```\"\n```x\nstill")
        #expect(MarkdownCode.parse("~~~python\nx = 1\n~~~") == "x = 1")
        #expect(MarkdownCode.parse("```swift\nlet a = 1\n") == "let a = 1")
    }

    @Test func unknownActionsDecodeAsNil() throws {
        let json = #"{"suggestions":[{"action":"LIST","probability":0.9},{"action":"SOMETHING","probability":0.99}]}"#
        let response = try JSONDecoder().decode(WritingSuggestionsResponse.self, from: Data(json.utf8))
        #expect(response.suggestions.map(\.action) == [.list, nil])
    }
}
