import Testing
@testable import Writeopia
import WrModels

private func state(_ steps: [StoryStep]) -> StoryState {
    StoryState(stories: ContentManager().positioned(steps))
}

private let gap = positionGap

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
        var step = initial.stories[gap]!
        // The text view adjusts the spans for the typed "\n" before asking for the line break.
        step.spans = SpansHandler.adjust(step.spans, from: "Hello world", to: "Hello \nworld")
        step.text = "Hello \nworld"

        let result = manager.onLineBreak(Action.LineBreak(storyStep: step, position: gap, cursor: 7), state: initial)

        #expect(texts(result) == ["Title", "Hello ", "world"])
        #expect(result.stories[2 * gap]?.spans == [SpanInfo(start: 0, end: 5, span: "BOLD")])
        #expect(result.stories[2 * gap]?.type == .text)
        #expect(result.focus == 2 * gap)
        #expect(result.selection == .cursor(0, at: 2 * gap))
    }

    @Test func lineBreakInTitleCreatesParagraph() {
        let initial = state([StoryStep(type: .title, text: "Title", position: 0)])
        var title = initial.stories[0]!
        title.text = "Title\n"

        let result = manager.onLineBreak(Action.LineBreak(storyStep: title, position: 0, cursor: 6), state: initial)

        #expect(texts(result) == ["Title", ""])
        #expect(result.stories[gap]?.type.number == StoryType.text.number)
        #expect(result.focus == gap)
    }

    @Test func lineBreakInChecklistContinuesTheList() {
        let initial = state([
            StoryStep(type: .title, text: "T", position: 0),
            StoryStep(type: .checkItem, text: "Milk", checked: true, position: 1),
        ])
        var item = initial.stories[gap]!
        item.text = "Milk\n"

        let result = manager.onLineBreak(Action.LineBreak(storyStep: item, position: gap, cursor: 5), state: initial)

        #expect(result.stories[2 * gap]?.type == .checkItem)
        #expect(result.stories[2 * gap]?.checked == false)
    }

    @Test func lineBreakOnEmptyItemAddsAnotherItem() {
        for type in [StoryType.unorderedListItem, .checkItem] {
            let initial = state([
                StoryStep(type: .title, text: "T", position: 0),
                StoryStep(type: type, text: "", checked: type == .checkItem ? false : nil, position: 1),
            ])
            var item = initial.stories[gap]!
            item.text = "\n"

            let result = manager.onLineBreak(Action.LineBreak(storyStep: item, position: gap, cursor: 1), state: initial)

            #expect(result.stories.count == 3)
            #expect(result.stories[gap]?.type == type)
            #expect(result.stories[gap]?.text == "")
            #expect(result.stories[2 * gap]?.type == type)
            #expect(result.stories[2 * gap]?.checked == (type == .checkItem ? false : nil))
            #expect(result.focus == 2 * gap)
        }
    }

    @Test func pastedLinesBecomeSteps() {
        let initial = state([StoryStep(type: .title, text: "T", position: 0), StoryStep(type: .text, text: "", position: 1)])
        var step = initial.stories[gap]!
        step.text = "a\nb\nc"

        let result = manager.onLineBreak(Action.LineBreak(storyStep: step, position: gap, cursor: 5), state: initial)

        #expect(texts(result) == ["T", "a", "b", "c"])
        // The new lines go after the step at 1, which keeps its position
        #expect(result.sortedPositions == [0, gap, 2 * gap, 3 * gap])
        #expect(result.selection == .cursor(1, at: 3 * gap))
    }

    @Test func eraseMergesTextAndSpansIntoPreviousLine() {
        let initial = state([
            StoryStep(type: .title, text: "T", position: 0),
            StoryStep(type: .text, text: "Hello ", position: 1),
            StoryStep(type: .text, text: "world", spans: [SpanInfo(start: 0, end: 5, span: "ITALIC")], position: 2),
        ])

        let result = manager.onErase(Action.EraseStory(storyStep: initial.stories[2 * gap]!, position: 2 * gap), state: initial)

        #expect(texts(result) == ["T", "Hello world"])
        #expect(result.stories[gap]?.spans == [SpanInfo(start: 6, end: 11, span: "ITALIC")])
        #expect(result.selection == .cursor(6, at: gap))
    }

    @Test func eraseAfterDividerRemovesTheDivider() {
        let initial = state([
            StoryStep(type: .title, text: "T", position: 0),
            StoryStep(type: .divider, position: 1),
            StoryStep(type: .text, text: "after", position: 2),
        ])

        let result = manager.onErase(Action.EraseStory(storyStep: initial.stories[2 * gap]!, position: 2 * gap), state: initial)

        #expect(texts(result) == ["T", "after"])
        // The step keeps its position, only the divider went away
        #expect(result.focus == 2 * gap)
    }

    @Test func eraseOnHeadingOrBlockRemovesTheTagFirst() {
        let initial = state([
            StoryStep(type: .title, text: "T", position: 0),
            StoryStep(type: .text, text: "Before", position: 1),
            StoryStep(type: .text, text: "Heading", tags: [TagInfo(tag: "H2"), TagInfo(tag: "HIGH_LIGHT_BLOCK")], position: 2),
        ])

        let plain = manager.onErase(Action.EraseStory(storyStep: initial.stories[2 * gap]!, position: 2 * gap), state: initial)
        #expect(plain.stories.count == 3)
        #expect(plain.stories[2 * gap]?.text == "Heading")
        #expect(plain.stories[2 * gap]?.tags.isEmpty == true)
        #expect(plain.focus == 2 * gap)

        // The next Backspace merges as usual.
        let merged = manager.onErase(Action.EraseStory(storyStep: plain.stories[2 * gap]!, position: 2 * gap), state: plain)
        #expect(merged.stories.count == 2)
        #expect(merged.stories[gap]?.text == "BeforeHeading")
    }

    @Test func eraseOnListItemTurnsItIntoParagraph() {
        let initial = state([StoryStep(type: .title, text: "T", position: 0), StoryStep(type: .checkItem, text: "x", checked: true, position: 1)])

        let result = manager.onErase(Action.EraseStory(storyStep: initial.stories[gap]!, position: gap), state: initial)

        #expect(result.stories[gap]?.type.number == StoryType.text.number)
        #expect(result.stories[gap]?.checked == nil)
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

        let moved = manager.moveRequest(Action.Move(positionFrom: gap, positionTo: 3 * gap), state: initial)
        #expect(texts(moved) == ["T", "b", "c", "a"])
        #expect(moved.sortedPositions == [0, 2 * gap, 3 * gap, 4 * gap])
        #expect(moved.stories[4 * gap]?.position == 4 * gap)

        let toTop = manager.moveRequest(Action.Move(positionFrom: 3 * gap, positionTo: 0), state: initial)
        #expect(texts(toTop) == ["T", "c", "a", "b"])
        // Half way between the title and the step at 1, nothing else moves
        #expect(toTop.sortedPositions == [0, (gap / 2), gap, 2 * gap])

        #expect(manager.moveRequest(Action.Move(positionFrom: 0, positionTo: 2 * gap), state: initial) == initial)
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
        // The steps keep their positions; the title goes one gap before the first
        #expect(loaded.sortedPositions == [2 - gap, 2, 5])
        #expect(loaded.sortedStories.map(\.position) == [2 - gap, 2, 5])
    }

    @Test func clickAtTheEndAddsParagraphOnce() {
        let initial = state([StoryStep(type: .title, text: "T", position: 0), StoryStep(type: .divider, position: 1)])

        let added = manager.clickAtTheEnd(state: initial)
        #expect(texts(added) == ["T", "<divider>", ""])
        #expect(added.focus == 2 * gap)

        let again = manager.clickAtTheEnd(state: added)
        #expect(again.stories.count == 3)
        #expect(again.focus == 2 * gap)
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
    let initial = StoryState(stories: ContentManager().positioned([
        StoryStep(type: .title, text: "T", position: 0),
        StoryStep(type: .text, text: "Hello world", position: 1),
    ]))

    @Test func italicAndUnderlineToggleLikeBold() {
        var state = manager.toggleSpan(.italic, at: gap, start: 0, end: 5, state: initial)
        state = manager.toggleSpan(.underline, at: gap, start: 6, end: 11, state: state)
        #expect(state.stories[gap]?.spans == [
            SpanInfo(start: 0, end: 5, span: "ITALIC"),
            SpanInfo(start: 6, end: 11, span: "UNDERLINE"),
        ])

        state = manager.toggleSpan(.italic, at: gap, start: 0, end: 5, state: state)
        #expect(state.stories[gap]?.spans == [SpanInfo(start: 6, end: 11, span: "UNDERLINE")])
    }

    @Test func highlightColorsReplaceEachOther() {
        var state = manager.toggleSpan(.highlightYellow, at: gap, start: 0, end: 11, state: initial)
        state = manager.toggleSpan(.highlightGreen, at: gap, start: 0, end: 5, state: state)

        #expect(state.stories[gap]?.spans.sorted { $0.start < $1.start } == [
            SpanInfo(start: 0, end: 5, span: "HIGHLIGHT_GREEN"),
            SpanInfo(start: 5, end: 11, span: "HIGHLIGHT"),
        ])
    }

    @Test func linksAreSetReplacedAndRemoved() {
        var state = manager.setLink("https://a.io", at: gap, start: 0, end: 5, state: initial)
        #expect(state.stories[gap]?.spans == [SpanInfo(start: 0, end: 5, span: "LINK", extra: "https://a.io")])

        // A neighbouring link keeps its own URL instead of merging.
        state = manager.setLink("https://b.io", at: gap, start: 5, end: 11, state: state)
        #expect(state.stories[gap]?.spans.count == 2)

        state = manager.setLink(nil, at: gap, start: 0, end: 11, state: state)
        #expect(state.stories[gap]?.spans.isEmpty == true)
    }

    @Test func spansNeedASelection() {
        #expect(manager.toggleSpan(.bold, at: gap, start: 3, end: 3, state: initial) == initial)
        #expect(manager.toggleSpan(.bold, at: 0, start: 0, end: 50, state: initial).stories[0]?.spans
            == [SpanInfo(start: 0, end: 1, span: "BOLD")])
    }
}

@Suite struct MarkdownTests {
    @Test func followsTheSdkFormat() {
        let markdown = DocumentToMarkdown.parse([
            StoryStep(type: .title, text: "Plan", position: 0),
            StoryStep(type: .text, text: "Intro", position: 1),
            StoryStep(type: .text, text: "Section", tags: [TagInfo(tag: "H2")], position: 2),
            StoryStep(type: .checkItem, text: "Task", checked: true, position: 3),
            StoryStep(type: .unorderedListItem, text: "Bullet", position: 4),
            StoryStep(type: .divider, position: 5),
            StoryStep(type: .aiAnswer, text: "Answer", position: 6),
        ])

        #expect(markdown == "# Plan\nIntro\n## Section\n[] Task\n- Bullet\nAnswer\n")
    }
}

@Suite struct SelectedLinesTests {
    let manager = WriteopiaManager()
    let initial = StoryState(stories: ContentManager().positioned([
        StoryStep(id: "t", type: .title, text: "T", position: 0),
        StoryStep(id: "a", type: .text, text: "One", position: 1),
        StoryStep(id: "b", type: .checkItem, text: "Two", checked: true, position: 2),
        StoryStep(id: "c", type: .divider, position: 3),
    ]))

    @Test func typeTogglesPerLineLikeTheSdk() {
        let state = manager.toggleType(.checkItem, positions: [0, gap, 2 * gap, 3 * gap], state: initial)

        #expect(state.stories[0]?.type.number == StoryType.title.number)
        #expect(state.stories[gap]?.type.number == StoryType.checkItem.number)
        #expect(state.stories[gap]?.checked == false)
        #expect(state.stories[2 * gap]?.type.number == StoryType.text.number)
        #expect(state.stories[2 * gap]?.checked == nil)
        #expect(state.stories[3 * gap]?.type.number == StoryType.divider.number)
    }

    @Test func boxAndCardTagsToggle() {
        var state = manager.toggleTag(.box, positions: [gap, 2 * gap], state: initial)
        #expect(state.stories[gap]?.hasTag("HIGH_LIGHT_BLOCK") == true)
        state = manager.toggleTag(.box, positions: [gap], state: state)
        #expect(state.stories[gap]?.hasTag("HIGH_LIGHT_BLOCK") == false)
        #expect(state.stories[2 * gap]?.hasTag("HIGH_LIGHT_BLOCK") == true)
    }

    @Test func headingsReplaceEachOther() {
        var state = manager.toggleHeading(.h1, positions: [gap], state: initial)
        #expect(state.stories[gap]?.headingLevel == 1)
        state = manager.toggleHeading(.h3, positions: [gap], state: state)
        #expect(state.stories[gap]?.headingLevel == 3)
        #expect(state.stories[gap]?.tags.count == 1)
        state = manager.toggleHeading(.h3, positions: [gap], state: state)
        #expect(state.stories[gap]?.headingLevel == nil)
    }

    @Test func deleteKeepsTheTitle() {
        let state = manager.deleteSteps([0, gap, 3 * gap], state: initial)
        #expect(state.sortedStories.map(\.id) == ["t", "b"])
    }

    @Test func textAndDocumentLinks() {
        #expect(manager.text(of: [2 * gap, gap, 3 * gap], state: initial) == "One\nTwo")

        let state = manager.addDocumentLink(after: gap, documentId: "p", title: "One", state: initial)
        // Half way between the first and the second step
        #expect(state.stories[(gap + gap / 2)]?.type.number == StoryType.documentLink.number)
        #expect(state.stories[(gap + gap / 2)]?.documentLink == DocumentLink(id: "p", title: "One"))
    }
}

@Suite struct PositionKeepingTests {
    let manager = WriteopiaManager()

    /// title, a, b, c at 0, 1, 1.5 and 2 gaps.
    private func document() -> StoryState {
        var stories: [Int: StoryStep] = [:]
        stories.place(StoryStep(id: "t", type: .title, text: "T", position: 0), at: 0)
        stories.place(StoryStep(id: "a", type: .text, text: "a", position: 0), at: gap)
        stories.place(StoryStep(id: "b", type: .text, text: "b", position: 0), at: (gap + gap / 2))
        stories.place(StoryStep(id: "c", type: .text, text: "c", position: 0), at: 2 * gap)
        return StoryState(stories: stories)
    }

    @Test func neighboursComeFromTheKeys() {
        let stories = document().stories
        #expect(stories.previousKey(before: (gap + gap / 2)) == gap)
        #expect(stories.nextKey(after: (gap + gap / 2)) == 2 * gap)
        #expect(stories.nextKey(after: 2 * gap) == nil)
        #expect(stories.lastKey == 2 * gap)
    }

    @Test func insertsTakeTheMiddleOrOneGapAfterTheLast() {
        var stories = document().stories
        #expect(stories.insert(StoryStep(type: .text, text: "x", position: 0), after: gap).key == (gap + gap / 4))
        #expect(stories.insert(StoryStep(type: .text, text: "y", position: 0), after: 2 * gap).key == 3 * gap)
        #expect(stories.insert(StoryStep(type: .text, text: "z", position: 0), before: (gap + gap / 2)).key == (gap + 3 * gap / 8))
        #expect(StoryState(stories: stories).sortedStories.map(\.text) == ["T", "a", "x", "z", "b", "c", "y"])
    }

    @Test func insertingWithPlentyOfRoomGoesOneGapFurther() {
        var stories: [Int: StoryStep] = [:]
        stories.place(StoryStep(type: .title, text: "T", position: 0), at: 0)
        stories.place(StoryStep(type: .text, text: "far", position: 0), at: 10 * gap)

        let insertion = stories.insert(
            [StoryStep(type: .text, text: "x", position: 0), StoryStep(type: .text, text: "y", position: 0)],
            after: 0
        )

        #expect(insertion.keys == [gap, 2 * gap])
    }

    @Test func whenNoWholeNumberIsLeftTheNextStepsAreSpreadOut() {
        var stories: [Int: StoryStep] = [:]
        stories.place(StoryStep(type: .title, text: "T", position: 0), at: 0)
        stories.place(StoryStep(type: .text, text: "a", position: 0), at: 1)
        stories.place(StoryStep(type: .text, text: "b", position: 0), at: 2)
        stories.place(StoryStep(type: .text, text: "c", position: 0), at: 3)

        let insertion = stories.insert(StoryStep(type: .text, text: "new", position: 0), after: 1)

        let state = StoryState(stories: stories)
        #expect(state.sortedStories.map(\.text) == ["T", "a", "new", "b", "c"])
        #expect(stories[1]?.text == "a")
        #expect(insertion.key == 1 + gap)
        #expect(insertion.moved == [1 + 2 * gap, 1 + 3 * gap])
        #expect(state.sortedStories.map(\.position) == state.sortedPositions)
    }

    @Test func aLineBreakGoesBetweenTheLineAndTheNextOne() {
        let initial = document()
        var step = initial.stories[(gap + gap / 2)]!
        step.text = "b\n"

        let result = manager.onLineBreak(Action.LineBreak(storyStep: step, position: (gap + gap / 2), cursor: 2), state: initial)

        #expect(result.sortedStories.map(\.text) == ["T", "a", "b", "", "c"])
        #expect(result.sortedPositions == [0, gap, (gap + gap / 2), (gap + 3 * gap / 4), 2 * gap])
        #expect(result.focus == (gap + 3 * gap / 4))
        #expect(result.stories[(gap + 3 * gap / 4)]?.position == (gap + 3 * gap / 4))
    }

    @Test func aLineBreakWithNoRoomSpreadsTheNextLinesOut() {
        var stories: [Int: StoryStep] = [:]
        stories.place(StoryStep(type: .title, text: "T", position: 0), at: 0)
        stories.place(StoryStep(type: .text, text: "a", position: 0), at: 1)
        stories.place(StoryStep(type: .text, text: "b", position: 0), at: 2)
        let initial = StoryState(stories: stories)
        var step = initial.stories[1]!
        step.text = "a\n"

        let result = manager.onLineBreak(Action.LineBreak(storyStep: step, position: 1, cursor: 2), state: initial)

        #expect(result.sortedStories.map(\.text) == ["T", "a", "", "b"])
        #expect(result.stories[1]?.text == "a")
        #expect(result.sortedStories.map(\.position) == result.sortedPositions)
    }

    @Test func stepsAddedAfterAStepSpreadUpToTheNextOne() {
        let initial = document()
        let result = manager.addSteps(
            [StoryStep(type: .text, text: "x", position: 0), StoryStep(type: .text, text: "y", position: 0)],
            after: gap,
            state: initial
        )

        #expect(result.sortedStories.map(\.text) == ["T", "a", "x", "y", "b", "c"])
        #expect(result.stories[gap]?.text == "a")
        #expect(result.stories[(gap + gap / 2)]?.text == "b")
    }

    @Test func aStepAddedBeforeAnotherGoesHalfWayToThePrevious() {
        let result = manager.addStep(StoryStep(type: .text, text: "h", position: 0), before: (gap + gap / 2), state: document())

        #expect(result.sortedStories.map(\.text) == ["T", "a", "h", "b", "c"])
        #expect(result.stories[(gap + gap / 4)]?.text == "h")
    }

    @Test func replacingAStepKeepsItsPositionForTheFirstReplacement() {
        let result = manager.replaceStep(
            id: "b",
            with: [StoryStep(type: .text, text: "b1", position: 0), StoryStep(type: .text, text: "b2", position: 0)],
            state: document()
        )

        #expect(result.sortedStories.map(\.text) == ["T", "a", "b1", "b2", "c"])
        #expect(result.stories[(gap + gap / 2)]?.text == "b1")
        #expect(result.stories[(gap + 3 * gap / 4)]?.text == "b2")
        #expect(result.stories[2 * gap]?.text == "c")
    }

    @Test func deletingKeepsThePositionsOfTheOthers() {
        let result = manager.deleteSteps([(gap + gap / 2)], state: document())
        #expect(result.sortedPositions == [0, gap, 2 * gap])

        let erased = manager.onErase(Action.EraseStory(storyStep: document().stories[2 * gap]!, position: 2 * gap), state: document())
        #expect(erased.sortedPositions == [0, gap, (gap + gap / 2)])
        #expect(erased.stories[(gap + gap / 2)]?.text == "bc")
    }

    @Test func loadingResolvesTwoStepsAtTheSamePosition() {
        let document = WrDocument(
            id: "d",
            title: "Plan",
            workspaceId: "w",
            content: [
                StoryStep(id: "t", type: .title, text: "Plan", position: 0),
                StoryStep(id: "x", type: .text, text: "x", position: gap),
                StoryStep(id: "y", type: .text, text: "y", position: gap),
                StoryStep(id: "z", type: .text, text: "z", position: 2 * gap),
            ]
        )

        let loaded = manager.loadDocument(document)

        #expect(loaded.sortedStories.map(\.id) == ["t", "x", "y", "z"])
        #expect(loaded.sortedPositions == [0, gap, (gap + gap / 2), 2 * gap])
    }
}
