import Foundation
import Testing
@testable import WriteopiaUI
import Writeopia
import WrModels

/// The uploaded pictures the user removes reach `onImagesRemoved`, so the app can free their
/// space on the backend; pictures still on the device and other steps don't.
@Suite struct RemovedImagesTests {
    private let uploaded = StoryStep(id: "img", type: .image, url: "https://storage.test/uploads/u/1-a.jpg", position: Rank.at(1))
    private let local = StoryStep(id: "local", type: .image, path: "/tmp/b.jpg", position: Rank.at(2))
    private let text = StoryStep(id: "a", type: .text, text: "Hello", position: Rank.at(3))

    private func manager() -> (WriteopiaStateManager, () -> [String]) {
        let manager = WriteopiaStateManager()
        var removed: [String] = []
        manager.onImagesRemoved = { steps in removed += steps.map(\.id) }
        manager.loadDocument(WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [
            StoryStep(id: "t", type: .title, text: "Doc", position: Rank.at(0)),
            uploaded, local, text,
        ]))
        return (manager, { removed })
    }

    @Test func deletingAnUploadedImageReportsIt() {
        let (manager, removed) = manager()

        manager.removeStep(stepId: "img")

        #expect(removed() == ["img"])
        #expect(manager.currentStory.stories.values.contains { $0.id == "img" } == false)
    }

    @Test func localImagesAndOtherStepsAreNotReported() {
        let (manager, removed) = manager()

        manager.removeStep(stepId: "local")
        manager.removeStep(stepId: "a")

        #expect(removed().isEmpty)
    }

    @Test func deletingSelectedLinesReportsTheUploadedImagesAmongThem() {
        let (manager, removed) = manager()
        manager.onSelected(stepId: "img", isSelected: true)
        manager.onSelected(stepId: "local", isSelected: true)
        manager.onSelected(stepId: "a", isSelected: true)

        manager.deleteSelectedLines()

        #expect(removed() == ["img"])
        #expect(manager.currentStory.stories.count == 1)
    }

    @Test func backspaceAtTheStartOfAnUploadedImageReportsIt() {
        let (manager, removed) = manager()

        manager.onErase(stepId: "img")

        #expect(removed() == ["img"])
    }
}
