import Testing
@testable import Writeopia
import WrModels

private func state(_ steps: [StoryStep]) -> StoryState {
    StoryState(stories: ContentManager().renumber(steps))
}

private func texts(_ state: StoryState) -> [String] {
    state.sortedStories.map { $0.text ?? "<\($0.type.name)>" }
}

@Suite struct SpansHandlerTests {
    let bold = SpanInfo(start: 2, end: 5, span: "BOLD")

    @Test func typingBeforeSpanShiftsIt() {
        let result = SpansHandler.adjust([bold], from: "hello world", to: "XXhello world")
        #expect(result == [SpanInfo(start: 4, end: 7, span: "BOLD")])
    }

    @Test func typingInsideSpanExtendsIt() {
        let result = SpansHandler.adjust([bold], from: "hello world", to: "helXlo world")
        #expect(result == [SpanInfo(start: 2, end: 6, span: "BOLD")])
    }

    @Test func typingAtTheEndOfSpanDoesNotExtendIt() {
        let result = SpansHandler.adjust([bold], from: "hello world", to: "helloX world")
        #expect(result == [bold])
    }

    @Test func deletingTheWholeSpanDropsIt() {
        let result = SpansHandler.adjust([bold], from: "hello world", to: "he world")
        #expect(result.isEmpty)
    }

    @Test func deletingPartOfSpanShrinksIt() {
        let result = SpansHandler.adjust([bold], from: "hello world", to: "helo world")
        #expect(result == [SpanInfo(start: 2, end: 4, span: "BOLD")])
    }

    @Test func splitsSpansByLine() {
        let lines = SpansHandler.splitLines("abc\ndef", spans: [SpanInfo(start: 1, end: 6, span: "ITALIC")])
        #expect(lines.map(\.text) == ["abc", "def"])
        #expect(lines[0].spans == [SpanInfo(start: 1, end: 3, span: "ITALIC")])
        #expect(lines[1].spans == [SpanInfo(start: 0, end: 2, span: "ITALIC")])
    }
}

@Suite struct ContentManagerTests {
    let manager = WriteopiaManager()

    @Test func lineBreakSplitsTheStepAndFocusesTheNewLine() {
        let initial = state([
            StoryStep(type: .title, text: "Title", position: 0),
            StoryStep(type: .text, text: "Hello world", spans: [SpanInfo(start: 6, end: 11, span: "BOLD")], position: 1),
        ])
        var step = initial.stories[1]!
        // The text view adjusts the spans for the typed "\n" before asking for the line break.
        step.spans = SpansHandler.adjust(step.spans, from: "Hello world", to: "Hello \nworld")
        step.text = "Hello \nworld"

        let result = manager.onLineBreak(Action.LineBreak(storyStep: step, position: 1, cursor: 7), state: initial)

        #expect(texts(result) == ["Title", "Hello ", "world"])
        #expect(result.stories[2]?.spans == [SpanInfo(start: 0, end: 5, span: "BOLD")])
        #expect(result.stories[2]?.type == .text)
        #expect(result.focus == 2)
        #expect(result.selection == .cursor(0, at: 2))
    }

    @Test func lineBreakInTitleCreatesParagraph() {
        let initial = state([StoryStep(type: .title, text: "Title", position: 0)])
        var title = initial.stories[0]!
        title.text = "Title\n"

        let result = manager.onLineBreak(Action.LineBreak(storyStep: title, position: 0, cursor: 6), state: initial)

        #expect(texts(result) == ["Title", ""])
        #expect(result.stories[1]?.type.number == StoryType.text.number)
        #expect(result.focus == 1)
    }

    @Test func lineBreakInChecklistContinuesTheList() {
        let initial = state([
            StoryStep(type: .title, text: "T", position: 0),
            StoryStep(type: .checkItem, text: "Milk", checked: true, position: 1),
        ])
        var item = initial.stories[1]!
        item.text = "Milk\n"

        let result = manager.onLineBreak(Action.LineBreak(storyStep: item, position: 1, cursor: 5), state: initial)

        #expect(result.stories[2]?.type == .checkItem)
        #expect(result.stories[2]?.checked == false)
    }

    @Test func lineBreakOnEmptyListItemLeavesTheList() {
        let initial = state([
            StoryStep(type: .title, text: "T", position: 0),
            StoryStep(type: .unorderedListItem, text: "", position: 1),
        ])
        var item = initial.stories[1]!
        item.text = "\n"

        let result = manager.onLineBreak(Action.LineBreak(storyStep: item, position: 1, cursor: 1), state: initial)

        #expect(result.stories.count == 2)
        #expect(result.stories[1]?.type.number == StoryType.text.number)
    }

    @Test func pastedLinesBecomeSteps() {
        let initial = state([StoryStep(type: .title, text: "T", position: 0), StoryStep(type: .text, text: "", position: 1)])
        var step = initial.stories[1]!
        step.text = "a\nb\nc"

        let result = manager.onLineBreak(Action.LineBreak(storyStep: step, position: 1, cursor: 5), state: initial)

        #expect(texts(result) == ["T", "a", "b", "c"])
        #expect(result.selection == .cursor(1, at: 3))
    }

    @Test func eraseMergesTextAndSpansIntoPreviousLine() {
        let initial = state([
            StoryStep(type: .title, text: "T", position: 0),
            StoryStep(type: .text, text: "Hello ", position: 1),
            StoryStep(type: .text, text: "world", spans: [SpanInfo(start: 0, end: 5, span: "ITALIC")], position: 2),
        ])

        let result = manager.onErase(Action.EraseStory(storyStep: initial.stories[2]!, position: 2), state: initial)

        #expect(texts(result) == ["T", "Hello world"])
        #expect(result.stories[1]?.spans == [SpanInfo(start: 6, end: 11, span: "ITALIC")])
        #expect(result.selection == .cursor(6, at: 1))
    }

    @Test func eraseAfterDividerRemovesTheDivider() {
        let initial = state([
            StoryStep(type: .title, text: "T", position: 0),
            StoryStep(type: .divider, position: 1),
            StoryStep(type: .text, text: "after", position: 2),
        ])

        let result = manager.onErase(Action.EraseStory(storyStep: initial.stories[2]!, position: 2), state: initial)

        #expect(texts(result) == ["T", "after"])
        #expect(result.focus == 1)
    }

    @Test func eraseOnListItemTurnsItIntoParagraph() {
        let initial = state([StoryStep(type: .title, text: "T", position: 0), StoryStep(type: .checkItem, text: "x", checked: true, position: 1)])

        let result = manager.onErase(Action.EraseStory(storyStep: initial.stories[1]!, position: 1), state: initial)

        #expect(result.stories[1]?.type.number == StoryType.text.number)
        #expect(result.stories[1]?.checked == nil)
    }

    @Test func eraseAtTheTitleDoesNothing() {
        let initial = state([StoryStep(type: .title, text: "T", position: 0)])
        #expect(manager.onErase(Action.EraseStory(storyStep: initial.stories[0]!, position: 0), state: initial) == initial)
    }

    @Test func moveKeepsTitleFirst() {
        let initial = state([
            StoryStep(type: .title, text: "T", position: 0),
            StoryStep(type: .text, text: "a", position: 1),
            StoryStep(type: .text, text: "b", position: 2),
            StoryStep(type: .text, text: "c", position: 3),
        ])

        let moved = manager.moveRequest(Action.Move(positionFrom: 1, positionTo: 3), state: initial)
        #expect(texts(moved) == ["T", "b", "c", "a"])

        let toTop = manager.moveRequest(Action.Move(positionFrom: 3, positionTo: 0), state: initial)
        #expect(texts(toTop) == ["T", "c", "a", "b"])

        #expect(manager.moveRequest(Action.Move(positionFrom: 0, positionTo: 2), state: initial) == initial)
    }

    @Test func loadDocumentPutsTitleFirstAndDropsEphemeralSteps() {
        let document = WrDocument(
            id: "d",
            title: "Plan",
            workspaceId: "w",
            content: [
                StoryStep(type: .text, text: "body", position: 5),
                StoryStep(type: .loading, position: 6),
                StoryStep(type: .divider, position: 2),
            ]
        )

        let loaded = manager.loadDocument(document)

        #expect(texts(loaded) == ["Plan", "<divider>", "body"])
        #expect(loaded.sortedPositions == [0, 1, 2])
    }

    @Test func clickAtTheEndAddsParagraphOnce() {
        let initial = state([StoryStep(type: .title, text: "T", position: 0), StoryStep(type: .divider, position: 1)])

        let added = manager.clickAtTheEnd(state: initial)
        #expect(texts(added) == ["T", "<divider>", ""])
        #expect(added.focus == 2)

        let again = manager.clickAtTheEnd(state: added)
        #expect(again.stories.count == 3)
        #expect(again.focus == 2)
    }
}

@Suite struct ToggleSpanTests {
    @Test func addsBoldToRange() {
        let result = SpansHandler.toggle("BOLD", start: 2, end: 5, in: [])
        #expect(result == [SpanInfo(start: 2, end: 5, span: "BOLD")])
    }

    @Test func mergesWithTouchingBoldSpans() {
        let spans = [SpanInfo(start: 0, end: 3, span: "BOLD"), SpanInfo(start: 6, end: 8, span: "BOLD")]
        let result = SpansHandler.toggle("BOLD", start: 3, end: 6, in: spans)
        #expect(result == [SpanInfo(start: 0, end: 8, span: "BOLD")])
    }

    @Test func removesBoldWhenRangeIsAlreadyBold() {
        let spans = [SpanInfo(start: 0, end: 10, span: "BOLD")]
        let result = SpansHandler.toggle("BOLD", start: 3, end: 6, in: spans)
        #expect(result == [SpanInfo(start: 0, end: 3, span: "BOLD"), SpanInfo(start: 6, end: 10, span: "BOLD")])
    }

    @Test func partiallyBoldRangeBecomesBold() {
        let spans = [SpanInfo(start: 0, end: 4, span: "BOLD")]
        let result = SpansHandler.toggle("BOLD", start: 2, end: 8, in: spans)
        #expect(result == [SpanInfo(start: 0, end: 8, span: "BOLD")])
    }

    @Test func keepsOtherSpans() {
        let italic = SpanInfo(start: 1, end: 4, span: "ITALIC")
        let result = SpansHandler.toggle("BOLD", start: 1, end: 4, in: [italic])
        #expect(result == [italic, SpanInfo(start: 1, end: 4, span: "BOLD")])
    }

    @Test func coverageAcrossAdjacentSpans() {
        let spans = [SpanInfo(start: 0, end: 3, span: "BOLD"), SpanInfo(start: 3, end: 6, span: "BOLD")]
        #expect(SpansHandler.isFullyCovered(spans, span: "BOLD", start: 1, end: 5))
        #expect(!SpansHandler.isFullyCovered(spans, span: "BOLD", start: 1, end: 7))
    }
}

@Suite struct SpanKindsTests {
    let manager = WriteopiaManager()
    let initial = StoryState(stories: ContentManager().renumber([
        StoryStep(type: .title, text: "T", position: 0),
        StoryStep(type: .text, text: "Hello world", position: 1),
    ]))

    @Test func italicAndUnderlineToggleLikeBold() {
        var state = manager.toggleSpan(.italic, at: 1, start: 0, end: 5, state: initial)
        state = manager.toggleSpan(.underline, at: 1, start: 6, end: 11, state: state)
        #expect(state.stories[1]?.spans == [
            SpanInfo(start: 0, end: 5, span: "ITALIC"),
            SpanInfo(start: 6, end: 11, span: "UNDERLINE"),
        ])

        state = manager.toggleSpan(.italic, at: 1, start: 0, end: 5, state: state)
        #expect(state.stories[1]?.spans == [SpanInfo(start: 6, end: 11, span: "UNDERLINE")])
    }

    @Test func highlightColorsReplaceEachOther() {
        var state = manager.toggleSpan(.highlightYellow, at: 1, start: 0, end: 11, state: initial)
        state = manager.toggleSpan(.highlightGreen, at: 1, start: 0, end: 5, state: state)

        #expect(state.stories[1]?.spans.sorted { $0.start < $1.start } == [
            SpanInfo(start: 0, end: 5, span: "HIGHLIGHT_GREEN"),
            SpanInfo(start: 5, end: 11, span: "HIGHLIGHT"),
        ])
    }

    @Test func linksAreSetReplacedAndRemoved() {
        var state = manager.setLink("https://a.io", at: 1, start: 0, end: 5, state: initial)
        #expect(state.stories[1]?.spans == [SpanInfo(start: 0, end: 5, span: "LINK", extra: "https://a.io")])

        // A neighbouring link keeps its own URL instead of merging.
        state = manager.setLink("https://b.io", at: 1, start: 5, end: 11, state: state)
        #expect(state.stories[1]?.spans.count == 2)

        state = manager.setLink(nil, at: 1, start: 0, end: 11, state: state)
        #expect(state.stories[1]?.spans.isEmpty == true)
    }

    @Test func spansNeedASelection() {
        #expect(manager.toggleSpan(.bold, at: 1, start: 3, end: 3, state: initial) == initial)
        #expect(manager.toggleSpan(.bold, at: 0, start: 0, end: 50, state: initial).stories[0]?.spans
            == [SpanInfo(start: 0, end: 1, span: "BOLD")])
    }
}
