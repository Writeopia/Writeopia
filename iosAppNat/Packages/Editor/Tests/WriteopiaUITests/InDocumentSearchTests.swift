import Foundation
import Testing
@testable import WriteopiaUI
import Writeopia
import WrModels

/// The search inside a document: which steps match, how they are drawn, and moving between them.
@Suite struct InDocumentSearchTests {
    private let document = WrDocument(id: "d", title: "Groceries", workspaceId: "w", content: [
        StoryStep(id: "t", type: .title, text: "Groceries", position: Rank.at(0)),
        StoryStep(id: "a", type: .text, text: "Buy some fresh bananas", position: Rank.at(1)),
        StoryStep(id: "b", type: .checkItem, text: "and milk", checked: false, position: Rank.at(2)),
        StoryStep(id: "c", type: .text, text: "Bananas for the cake", position: Rank.at(3)),
    ])

    private func spans(_ manager: WriteopiaStateManager, of stepId: String) -> [String] {
        manager.toDraw.first { $0.id == stepId }?.storyStep.spans.map(\.span) ?? []
    }

    @Test func theStepsHoldingEveryWordMatchInOrderAndAreHighlighted() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document)

        manager.search("BANANA")

        #expect(manager.searchMatches == ["a", "c"])
        #expect(manager.searchIndex == 0)
        #expect(manager.activeSearchStepId == "a")
        #expect(spans(manager, of: "a") == [Span.highlightGreen.rawValue])
        #expect(spans(manager, of: "c") == [Span.highlightYellow.rawValue])
        #expect(spans(manager, of: "b").isEmpty)
        #expect(manager.scrollRequest?.stepId == "a")
        // Only drawn: the document keeps its own spans.
        #expect(manager.currentStory.sortedStories.allSatisfy { $0.spans.isEmpty })
    }

    @Test func everyWordIsNeededAndWordsInsideWordsCount() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document)

        manager.search("banana cake")
        #expect(manager.searchMatches == ["c"])

        manager.search("nana")
        #expect(manager.searchMatches == ["a", "c"])

        manager.search("banana missing")
        #expect(manager.searchMatches.isEmpty)
        #expect(manager.activeSearchStepId == nil)
    }

    @Test func nextAndPreviousWrapAroundAndScroll() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document)
        manager.search("banana")

        manager.nextSearchResult()
        #expect(manager.activeSearchStepId == "c")
        #expect(spans(manager, of: "c") == [Span.highlightGreen.rawValue])
        #expect(manager.scrollRequest?.stepId == "c")

        manager.nextSearchResult()
        #expect(manager.activeSearchStepId == "a")

        manager.previousSearchResult()
        #expect(manager.activeSearchStepId == "c")
    }

    @Test func clearingTheSearchRemovesTheHighlights() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document)
        manager.search("banana")

        manager.clearSearch()

        #expect(manager.searchMatches.isEmpty)
        #expect(manager.searchQuery.isEmpty)
        #expect(manager.toDraw.allSatisfy { $0.storyStep.spans.isEmpty })
    }

    @Test func aSearchLeftOpenFollowsTheDocumentLoadedNext() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document)
        manager.search("milk")
        #expect(manager.searchMatches == ["b"])

        manager.loadDocument(WrDocument(id: "e", title: "Other", workspaceId: "w", content: [
            StoryStep(id: "t2", type: .title, text: "Other", position: Rank.at(0)),
            StoryStep(id: "x", type: .text, text: "Milk and honey", position: Rank.at(1)),
        ]))

        #expect(manager.searchMatches == ["x"])
        #expect(manager.activeSearchStepId == "x")
    }

    @Test func aHandledScrollRequestIsCleared() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document)
        manager.search("milk")

        let request = try! #require(manager.scrollRequest)
        manager.scrollRequestHandled(request)

        #expect(manager.scrollRequest == nil)
    }
}
