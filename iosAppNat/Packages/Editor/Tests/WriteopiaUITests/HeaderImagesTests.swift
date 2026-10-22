import Foundation
import Testing
@testable import WriteopiaUI
import WrModels

private func document(_ steps: [StoryStep]) -> WrDocument {
    WrDocument(id: "d", title: "Doc", workspaceId: "w", content: steps)
}

/// The backend in memory: draws numbered pictures and keeps the approved ones.
private final class FakeHeaderImages: HeaderImageServing {
    var saved: [String]
    var failure: String?
    var saveFailure: String?
    var generatedFor: [(String, String)] = []

    init(saved: [String] = [], failure: String? = nil, saveFailure: String? = nil) {
        self.saved = saved
        self.failure = failure
        self.saveFailure = saveFailure
    }

    func generateHeaderImage(documentTitle: String, documentText: String) async throws -> String {
        generatedFor.append((documentTitle, documentText))
        if let failure { throw FakeError(message: failure) }
        return "https://cdn.test/header-images/w/\(generatedFor.count).png"
    }

    func headerImages() async throws -> [String] { saved }

    func saveHeaderImage(url: String) async throws {
        if let saveFailure { throw FakeError(message: saveFailure) }
        saved.insert(url, at: 0)
    }
}

private struct FakeError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

@Suite struct HeaderImageTests {
    @Test func aPictureReplacesTheColorAndAColorReplacesThePicture() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document([StoryStep(id: "t", type: .title, text: "Doc", position: Rank.at(0))]))

        manager.setHeaderColor(WriteopiaStateManager.headerColors[2])
        manager.setHeaderImage("https://cdn.test/a.png")
        #expect(manager.headerImageUrl == "https://cdn.test/a.png")
        #expect(manager.step(withId: "t")?.url == "https://cdn.test/a.png")
        #expect(manager.headerColor == nil)

        manager.setHeaderColor(WriteopiaStateManager.headerColors[2])
        #expect(manager.headerImageUrl == nil)
        #expect(manager.headerColor == WriteopiaStateManager.headerColors[2])

        manager.setHeaderImage("https://cdn.test/a.png")
        manager.setHeaderColor(nil)
        #expect(manager.headerImageUrl == nil)
        #expect(manager.step(withId: "t")?.decoration == nil)
    }

    @Test func aLockedDocumentKeepsItsHeader() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document([StoryStep(id: "t", type: .title, text: "Doc", position: Rank.at(0))]))
        manager.isEditable = false

        manager.setHeaderImage("https://cdn.test/a.png")
        #expect(manager.headerImageUrl == nil)
    }

    @Test func aDrawnPictureWaitsForApprovalThenJoinsTheWorkspace() async {
        let service = FakeHeaderImages(saved: ["https://cdn.test/old.png"])
        var approved: [String] = []
        let model = HeaderImagesModel(
            service: service,
            documentTitle: { "Coastal Journal" },
            documentText: { "The lighthouse." },
            onApproved: { approved.append($0) }
        )
        await model.load()
        #expect(model.images == ["https://cdn.test/old.png"])

        model.generate()
        #expect(model.isGenerating)
        await model.waitForGeneration()
        #expect(model.generation == .candidate("https://cdn.test/header-images/w/1.png"))
        #expect(approved.isEmpty)

        // Trying another draws a new candidate.
        model.generate()
        await model.waitForGeneration()
        #expect(model.generation == .candidate("https://cdn.test/header-images/w/2.png"))
        #expect(service.generatedFor.count == 2)
        #expect(service.generatedFor.first?.0 == "Coastal Journal")

        await model.approve()
        #expect(service.saved == ["https://cdn.test/header-images/w/2.png", "https://cdn.test/old.png"])
        #expect(approved == ["https://cdn.test/header-images/w/2.png"])
        #expect(model.images == ["https://cdn.test/header-images/w/2.png", "https://cdn.test/old.png"])
        #expect(model.generation == .idle)
    }

    @Test func aDiscardedPictureJoinsNothing() async {
        let service = FakeHeaderImages()
        var approved: [String] = []
        let model = HeaderImagesModel(
            service: service,
            documentTitle: { "" },
            documentText: { "" },
            onApproved: { approved.append($0) }
        )

        model.generate()
        await model.waitForGeneration()
        model.discard()

        #expect(model.generation == .idle)
        #expect(approved.isEmpty)
        #expect(service.saved.isEmpty)
    }

    @Test func aFailureSaysWhy() async {
        let model = HeaderImagesModel(
            service: FakeHeaderImages(failure: "Monthly AI quota exceeded"),
            documentTitle: { "" },
            documentText: { "" },
            onApproved: { _ in }
        )

        model.generate()
        await model.waitForGeneration()

        #expect(model.generation == .failed("Monthly AI quota exceeded"))
    }

    @Test func aPictureTheBackendDidNotKeepStaysACandidate() async {
        let service = FakeHeaderImages(saveFailure: "Service unavailable")
        var approved: [String] = []
        let model = HeaderImagesModel(
            service: service,
            documentTitle: { "" },
            documentText: { "" },
            onApproved: { approved.append($0) }
        )

        model.generate()
        await model.waitForGeneration()
        await model.approve()

        #expect(model.generation == .candidate("https://cdn.test/header-images/w/1.png"))
        #expect(model.saveFailed)
        #expect(model.saveError == "Service unavailable")
        #expect(!model.isSaving)
        #expect(approved.isEmpty)
        #expect(model.images.isEmpty)

        // Opening the menu again doesn't bring it in either.
        await model.load()
        #expect(model.images.isEmpty)

        // Approving again, once the backend answers, keeps it.
        service.saveFailure = nil
        await model.approve()
        #expect(approved == ["https://cdn.test/header-images/w/1.png"])
        #expect(model.images == ["https://cdn.test/header-images/w/1.png"])
        #expect(model.generation == .idle)
        #expect(!model.saveFailed)
    }
}
