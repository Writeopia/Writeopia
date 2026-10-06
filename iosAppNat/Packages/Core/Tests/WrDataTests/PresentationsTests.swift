import Foundation
import Testing
@testable import WrData
import WrModels

private let sample = """
    ## Slide 1

    This is a description explaining why the sky is blue! lalalala
    ---

    ### Slide 2

    Isn't it cool that I can create the second slide??

    [] This is a check box!
    - This is a list item!
    ---

    ### Thank you!!
    The presentation is over!
    """

@Suite struct PresentationMarkdownTests {
    @Test func everyDividerEndsASlideAndTheHeadingIsItsTitle() {
        let slides = PresentationMarkdown.parse(sample)

        #expect(slides.map(\.title) == ["Slide 1", "Slide 2", "Thank you!!"])
        #expect(slides[0].steps.map(\.text) == ["This is a description explaining why the sky is blue! lalalala"])
        #expect(slides[1].steps.map(\.type.number) == [StoryType.text.number, StoryType.checkItem.number, StoryType.unorderedListItem.number])
        #expect(slides[1].steps[1].text == "This is a check box!")
        #expect(slides[1].steps[1].checked == false)
        #expect(slides[1].steps[2].text == "This is a list item!")
        #expect(slides[1].steps.map(\.position) == [1, 2, 3])
        #expect(slides[2].steps.map(\.text) == ["The presentation is over!"])
    }

    @Test func withoutDividersEveryHeadingStartsASlide() {
        let slides = PresentationMarkdown.parse("""
            # Intro
            Hello
            ## Part two
            - one
            - two
            """)

        #expect(slides.map(\.title) == ["Intro", "Part two"])
        #expect(slides[1].steps.count == 2)
    }

    @Test func aCodeFenceAroundTheWholeAnswerIsDropped() {
        let slides = PresentationMarkdown.parse("```markdown\n\(sample)\n```\n")

        #expect(slides.map(\.title) == ["Slide 1", "Slide 2", "Thank you!!"])
        #expect(slides[0].steps.first?.type.number == StoryType.text.number)
    }

    @Test func emptySlidesAreDroppedAndAMissingHeadingLeavesAnEmptyTitle() {
        let slides = PresentationMarkdown.parse("""
            ---

            Just a paragraph
            ---
            ---
            ## Last
            ---
            """)

        #expect(slides.count == 2)
        #expect(slides[0].title == "")
        #expect(slides[0].steps.map(\.text) == ["Just a paragraph"])
        #expect(slides[1].title == "Last")
        #expect(slides[1].steps.isEmpty)
    }

    @Test func theLinesOfAParagraphAreJoinedAndBlankLinesSeparateParagraphs() {
        let slides = PresentationMarkdown.parse("""
            ## Wrapped
            The model wrapped this sentence
            over two lines.

            A second paragraph.
            - an item
            - another item
            Back to prose.
            ```
            code line one
            code line two
            ```
            ---
            """)

        #expect(slides[0].steps.map(\.text) == [
            "The model wrapped this sentence over two lines.", "A second paragraph.", "an item", "another item", "Back to prose.",
            "code line one", "code line two",
        ])
        #expect(slides[0].steps[2].type.number == StoryType.unorderedListItem.number)
        #expect(slides[0].steps[4].type.number == StoryType.text.number)
        #expect(slides[0].steps[5].type.number == StoryType.codeBlock.number)
    }

    @Test func nothingGivesNoSlides() {
        #expect(PresentationMarkdown.parse("").isEmpty)
        #expect(PresentationMarkdown.parse("\n---\n\n").isEmpty)
    }

    @Test func aSlideBecomesADocumentWithItsTitleFirst() {
        let slide = PresentationMarkdown.parse(sample)[1]
        let document = slide.asDocument(id: "p-1")

        #expect(document.title == "Slide 2")
        #expect(document.content.first?.type.number == StoryType.title.number)
        #expect(document.content.first?.text == "Slide 2")
        #expect(document.content.map(\.position) == [0, 1, 2, 3])
    }
}

@Suite struct LocalPresentationsTests {
    let directory = FileManager.default.temporaryDirectory
        .appending(path: "wr tests \(UUID().uuidString)", directoryHint: .isDirectory)

    private func presentation(documentId: String, title: String, createdAt: Int64) -> Presentation {
        Presentation(documentId: documentId, title: title, createdAt: createdAt, slides: PresentationMarkdown.parse(sample, now: createdAt))
    }

    @Test func savesEveryStepAsARowAndReadsThemBack() async throws {
        let repository = LocalDocumentsRepository(directory: directory, seedsWelcome: false)
        let document = try await repository.createDocument(title: "Sky", parentId: Folder.rootId)
        let saved = presentation(documentId: document.id, title: "Slide 1", createdAt: 10)

        try await repository.savePresentation(saved)
        let loaded = try await repository.presentation(id: saved.id)

        #expect(loaded == saved)
        #expect(loaded?.slides.map(\.title) == ["Slide 1", "Slide 2", "Thank you!!"])
        #expect(loaded?.slides[1].steps.map(\.type.number) == [StoryType.text.number, StoryType.checkItem.number, StoryType.unorderedListItem.number])
    }

    @Test func listsThePresentationsOfADocumentNewestFirst() async throws {
        let repository = LocalDocumentsRepository(directory: directory, seedsWelcome: false)
        let old = presentation(documentId: "doc", title: "Old", createdAt: 1)
        let recent = presentation(documentId: "doc", title: "Recent", createdAt: 2)
        let other = presentation(documentId: "other", title: "Other", createdAt: 3)
        for item in [old, recent, other] {
            try await repository.savePresentation(item)
        }

        let listed = try await repository.presentations(ofDocument: "doc")

        #expect(listed.map(\.title) == ["Recent", "Old"])
        #expect(listed.allSatisfy { $0.slides.count == 3 })
    }

    @Test func searchFindsThePresentationsByTitleNewestFirst() async throws {
        let repository = LocalDocumentsRepository(directory: directory, seedsWelcome: false)
        let old = presentation(documentId: "doc", title: "Sky colors", createdAt: 1)
        let recent = presentation(documentId: "other", title: "Why the SKY is blue", createdAt: 2)
        let unrelated = presentation(documentId: "doc", title: "Oceans", createdAt: 3)
        for item in [old, recent, unrelated] {
            try await repository.savePresentation(item)
        }

        let found = try await repository.searchPresentations(query: " sky ")

        #expect(found.map(\.title) == ["Why the SKY is blue", "Sky colors"])
        #expect(found.map(\.documentId) == ["other", "doc"])
        #expect(try await repository.searchPresentations(query: "  ").isEmpty)
    }

    @Test func savingAgainReplacesTheSlides() async throws {
        let repository = LocalDocumentsRepository(directory: directory, seedsWelcome: false)
        var saved = presentation(documentId: "doc", title: "Sky", createdAt: 1)
        try await repository.savePresentation(saved)

        saved.slides = [Slide(title: "Only one", steps: [StoryStep(type: .text, text: "Left", position: 1)])]
        try await repository.savePresentation(saved)

        let loaded = try await repository.presentation(id: saved.id)
        #expect(loaded?.slides.map(\.title) == ["Only one"])
        #expect(loaded?.slides[0].steps.map(\.text) == ["Left"])
    }

    @Test func deletingRemovesThePresentationAndItsSteps() async throws {
        let repository = LocalDocumentsRepository(directory: directory, seedsWelcome: false)
        let saved = presentation(documentId: "doc", title: "Sky", createdAt: 1)
        try await repository.savePresentation(saved)

        try await repository.deletePresentation(id: saved.id)

        #expect(try await repository.presentation(id: saved.id) == nil)
        #expect(try await repository.presentations(ofDocument: "doc").isEmpty)
    }

    @Test func deletingTheDocumentDeletesItsPresentations() async throws {
        let repository = LocalDocumentsRepository(directory: directory, seedsWelcome: false)
        let document = try await repository.createDocument(title: "Sky", parentId: Folder.rootId)
        let saved = presentation(documentId: document.id, title: "Sky", createdAt: 1)
        try await repository.savePresentation(saved)

        try await repository.deleteDocument(id: document.id)

        #expect(try await repository.presentation(id: saved.id) == nil)
    }
}
