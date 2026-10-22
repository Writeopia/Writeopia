import Testing
@testable import Writeopia
import WrModels

private func state(_ steps: [StoryStep]) -> StoryState {
    StoryState(stories: ContentManager().positioned(steps))
}

/// The rank of the `n`th step of a document written in order.
private func r(_ n: Int) -> String { Rank.at(n) }

/// The rank the code puts between `a` and `b` with the random suffix off, as in these tests.
private func mid(_ a: String?, _ b: String?) -> String { Rank.between(a, b, jitter: false) }

/// "b" of the position tests, between the steps at 1 and 2.
private var b: String { mid(r(1), r(2)) }

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
    init() { Rank.jitter = false }

    let manager = WriteopiaManager()

    @Test func lineBreakSplitsTheStepAndFocusesTheNewLine() {
        let initial = state([
            StoryStep(type: .title, text: "Title", position: r(0)),
            StoryStep(type: .text, text: "Hello world", spans: [SpanInfo(start: 6, end: 11, span: "BOLD")], position: r(1)),
        ])
        var step = initial.stories[r(1)]!
        // The text view adjusts the spans for the typed "\n" before asking for the line break.
        step.spans = SpansHandler.adjust(step.spans, from: "Hello world", to: "Hello \nworld")
        step.text = "Hello \nworld"

        let result = manager.onLineBreak(Action.LineBreak(storyStep: step, position: r(1), cursor: 7), state: initial)

        #expect(texts(result) == ["Title", "Hello ", "world"])
        #expect(result.stories[r(2)]?.spans == [SpanInfo(start: 0, end: 5, span: "BOLD")])
        #expect(result.stories[r(2)]?.type == .text)
        #expect(result.focus == r(2))
        #expect(result.selection == .cursor(0, at: r(2)))
    }

    @Test func lineBreakInTitleCreatesParagraph() {
        let initial = state([StoryStep(type: .title, text: "Title", position: r(0))])
        var title = initial.stories[r(0)]!
        title.text = "Title\n"

        let result = manager.onLineBreak(Action.LineBreak(storyStep: title, position: r(0), cursor: 6), state: initial)

        #expect(texts(result) == ["Title", ""])
        #expect(result.stories[r(1)]?.type.number == StoryType.text.number)
        #expect(result.focus == r(1))
    }

    @Test func lineBreakInChecklistContinuesTheList() {
        let initial = state([
            StoryStep(type: .title, text: "T", position: r(0)),
            StoryStep(type: .checkItem, text: "Milk", checked: true, position: r(1)),
        ])
        var item = initial.stories[r(1)]!
        item.text = "Milk\n"

        let result = manager.onLineBreak(Action.LineBreak(storyStep: item, position: r(1), cursor: 5), state: initial)

        #expect(result.stories[r(2)]?.type == .checkItem)
        #expect(result.stories[r(2)]?.checked == false)
    }

    @Test func lineBreakOnEmptyItemAddsAnotherItem() {
        for type in [StoryType.unorderedListItem, .checkItem] {
            let initial = state([
                StoryStep(type: .title, text: "T", position: r(0)),
                StoryStep(type: type, text: "", checked: type == .checkItem ? false : nil, position: r(1)),
            ])
            var item = initial.stories[r(1)]!
            item.text = "\n"

            let result = manager.onLineBreak(Action.LineBreak(storyStep: item, position: r(1), cursor: 1), state: initial)

            #expect(result.stories.count == 3)
            #expect(result.stories[r(1)]?.type == type)
            #expect(result.stories[r(1)]?.text == "")
            #expect(result.stories[r(2)]?.type == type)
            #expect(result.stories[r(2)]?.checked == (type == .checkItem ? false : nil))
            #expect(result.focus == r(2))
        }
    }

    @Test func pastedLinesBecomeSteps() {
        let initial = state([StoryStep(type: .title, text: "T", position: r(0)), StoryStep(type: .text, text: "", position: r(1))])
        var step = initial.stories[r(1)]!
        step.text = "a\nb\nc"

        let result = manager.onLineBreak(Action.LineBreak(storyStep: step, position: r(1), cursor: 5), state: initial)

        #expect(texts(result) == ["T", "a", "b", "c"])
        // The new lines go after the step at 1, which keeps its position
        #expect(result.sortedPositions == [r(0), r(1), r(2), r(3)])
        #expect(result.selection == .cursor(1, at: r(3)))
    }

    @Test func eraseMergesTextAndSpansIntoPreviousLine() {
        let initial = state([
            StoryStep(type: .title, text: "T", position: r(0)),
            StoryStep(type: .text, text: "Hello ", position: r(1)),
            StoryStep(type: .text, text: "world", spans: [SpanInfo(start: 0, end: 5, span: "ITALIC")], position: r(2)),
        ])

        let result = manager.onErase(Action.EraseStory(storyStep: initial.stories[r(2)]!, position: r(2)), state: initial)

        #expect(texts(result) == ["T", "Hello world"])
        #expect(result.stories[r(1)]?.spans == [SpanInfo(start: 6, end: 11, span: "ITALIC")])
        #expect(result.selection == .cursor(6, at: r(1)))
    }

    @Test func eraseAfterDividerRemovesTheDivider() {
        let initial = state([
            StoryStep(type: .title, text: "T", position: r(0)),
            StoryStep(type: .divider, position: r(1)),
            StoryStep(type: .text, text: "after", position: r(2)),
        ])

        let result = manager.onErase(Action.EraseStory(storyStep: initial.stories[r(2)]!, position: r(2)), state: initial)

        #expect(texts(result) == ["T", "after"])
        // The step keeps its position, only the divider went away
        #expect(result.focus == r(2))
    }

    @Test func eraseOnHeadingOrBlockRemovesTheTagFirst() {
        let initial = state([
            StoryStep(type: .title, text: "T", position: r(0)),
            StoryStep(type: .text, text: "Before", position: r(1)),
            StoryStep(type: .text, text: "Heading", tags: [TagInfo(tag: "H2"), TagInfo(tag: "HIGH_LIGHT_BLOCK")], position: r(2)),
        ])

        let plain = manager.onErase(Action.EraseStory(storyStep: initial.stories[r(2)]!, position: r(2)), state: initial)
        #expect(plain.stories.count == 3)
        #expect(plain.stories[r(2)]?.text == "Heading")
        #expect(plain.stories[r(2)]?.tags.isEmpty == true)
        #expect(plain.focus == r(2))

        // The next Backspace merges as usual.
        let merged = manager.onErase(Action.EraseStory(storyStep: plain.stories[r(2)]!, position: r(2)), state: plain)
        #expect(merged.stories.count == 2)
        #expect(merged.stories[r(1)]?.text == "BeforeHeading")
    }

    @Test func eraseOnListItemTurnsItIntoParagraph() {
        let initial = state([StoryStep(type: .title, text: "T", position: r(0)), StoryStep(type: .checkItem, text: "x", checked: true, position: r(1))])

        let result = manager.onErase(Action.EraseStory(storyStep: initial.stories[r(1)]!, position: r(1)), state: initial)

        #expect(result.stories[r(1)]?.type.number == StoryType.text.number)
        #expect(result.stories[r(1)]?.checked == nil)
    }

    @Test func eraseAtTheTitleDoesNothing() {
        let initial = state([StoryStep(type: .title, text: "T", position: r(0))])
        #expect(manager.onErase(Action.EraseStory(storyStep: initial.stories[r(0)]!, position: r(0)), state: initial) == initial)
    }

    @Test func moveKeepsTitleFirst() {
        let initial = state([
            StoryStep(type: .title, text: "T", position: r(0)),
            StoryStep(type: .text, text: "a", position: r(1)),
            StoryStep(type: .text, text: "b", position: r(2)),
            StoryStep(type: .text, text: "c", position: r(3)),
        ])

        let moved = manager.moveRequest(Action.Move(positionFrom: r(1), positionTo: r(3)), state: initial)
        #expect(texts(moved) == ["T", "b", "c", "a"])
        #expect(moved.sortedPositions == [r(0), r(2), r(3), r(4)])
        #expect(moved.stories[r(4)]?.position == r(4))

        let toTop = manager.moveRequest(Action.Move(positionFrom: r(3), positionTo: r(0)), state: initial)
        #expect(texts(toTop) == ["T", "c", "a", "b"])
        // Half way between the title and the step at 1, nothing else moves
        #expect(toTop.sortedPositions == [r(0), mid(r(0), r(1)), r(1), r(2)])

        #expect(manager.moveRequest(Action.Move(positionFrom: r(0), positionTo: r(2)), state: initial) == initial)
    }

    @Test func loadDocumentPutsTitleFirstAndDropsEphemeralSteps() {
        let document = WrDocument(
            id: "d",
            title: "Plan",
            workspaceId: "w",
            content: [
                StoryStep(type: .text, text: "body", position: r(5)),
                StoryStep(type: .loading, position: r(6)),
                StoryStep(type: .divider, position: r(2)),
            ]
        )

        let loaded = manager.loadDocument(document)

        #expect(texts(loaded) == ["Plan", "<divider>", "body"])
        // The steps keep their positions; the title goes one integer before the first
        #expect(loaded.sortedPositions == [r(1), r(2), r(5)])
        #expect(loaded.sortedStories.map(\.position) == [r(1), r(2), r(5)])
    }

    @Test func clickAtTheEndAddsParagraphOnce() {
        let initial = state([StoryStep(type: .title, text: "T", position: r(0)), StoryStep(type: .divider, position: r(1))])

        let added = manager.clickAtTheEnd(state: initial)
        #expect(texts(added) == ["T", "<divider>", ""])
        #expect(added.focus == r(2))

        let again = manager.clickAtTheEnd(state: added)
        #expect(again.stories.count == 3)
        #expect(again.focus == r(2))
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
        StoryStep(type: .title, text: "T", position: r(0)),
        StoryStep(type: .text, text: "Hello world", position: r(1)),
    ]))

    @Test func italicAndUnderlineToggleLikeBold() {
        var state = manager.toggleSpan(.italic, at: r(1), start: 0, end: 5, state: initial)
        state = manager.toggleSpan(.underline, at: r(1), start: 6, end: 11, state: state)
        #expect(state.stories[r(1)]?.spans == [
            SpanInfo(start: 0, end: 5, span: "ITALIC"),
            SpanInfo(start: 6, end: 11, span: "UNDERLINE"),
        ])

        state = manager.toggleSpan(.italic, at: r(1), start: 0, end: 5, state: state)
        #expect(state.stories[r(1)]?.spans == [SpanInfo(start: 6, end: 11, span: "UNDERLINE")])
    }

    @Test func highlightColorsReplaceEachOther() {
        var state = manager.toggleSpan(.highlightYellow, at: r(1), start: 0, end: 11, state: initial)
        state = manager.toggleSpan(.highlightGreen, at: r(1), start: 0, end: 5, state: state)

        #expect(state.stories[r(1)]?.spans.sorted { $0.start < $1.start } == [
            SpanInfo(start: 0, end: 5, span: "HIGHLIGHT_GREEN"),
            SpanInfo(start: 5, end: 11, span: "HIGHLIGHT"),
        ])
    }

    @Test func linksAreSetReplacedAndRemoved() {
        var state = manager.setLink("https://a.io", at: r(1), start: 0, end: 5, state: initial)
        #expect(state.stories[r(1)]?.spans == [SpanInfo(start: 0, end: 5, span: "LINK", extra: "https://a.io")])

        // A neighbouring link keeps its own URL instead of merging.
        state = manager.setLink("https://b.io", at: r(1), start: 5, end: 11, state: state)
        #expect(state.stories[r(1)]?.spans.count == 2)

        state = manager.setLink(nil, at: r(1), start: 0, end: 11, state: state)
        #expect(state.stories[r(1)]?.spans.isEmpty == true)
    }

    @Test func spansNeedASelection() {
        #expect(manager.toggleSpan(.bold, at: r(1), start: 3, end: 3, state: initial) == initial)
        #expect(manager.toggleSpan(.bold, at: r(0), start: 0, end: 50, state: initial).stories[r(0)]?.spans
            == [SpanInfo(start: 0, end: 1, span: "BOLD")])
    }
}

@Suite struct MarkdownTests {
    @Test func followsTheSdkFormat() {
        let markdown = DocumentToMarkdown.parse([
            StoryStep(type: .title, text: "Plan", position: r(0)),
            StoryStep(type: .text, text: "Intro", position: r(1)),
            StoryStep(type: .text, text: "Section", tags: [TagInfo(tag: "H2")], position: r(2)),
            StoryStep(type: .checkItem, text: "Task", checked: true, position: r(3)),
            StoryStep(type: .unorderedListItem, text: "Bullet", position: r(4)),
            StoryStep(type: .divider, position: r(5)),
            StoryStep(type: .aiAnswer, text: "Answer", position: r(6)),
        ])

        #expect(markdown == "# Plan\nIntro\n## Section\n[] Task\n- Bullet\nAnswer\n")
    }
}

@Suite struct SelectedLinesTests {
    init() { Rank.jitter = false }

    let manager = WriteopiaManager()
    let initial = StoryState(stories: ContentManager().positioned([
        StoryStep(id: "t", type: .title, text: "T", position: r(0)),
        StoryStep(id: "a", type: .text, text: "One", position: r(1)),
        StoryStep(id: "b", type: .checkItem, text: "Two", checked: true, position: r(2)),
        StoryStep(id: "c", type: .divider, position: r(3)),
    ]))

    @Test func typeTogglesPerLineLikeTheSdk() {
        let state = manager.toggleType(.checkItem, positions: [r(0), r(1), r(2), r(3)], state: initial)

        #expect(state.stories[r(0)]?.type.number == StoryType.title.number)
        #expect(state.stories[r(1)]?.type.number == StoryType.checkItem.number)
        #expect(state.stories[r(1)]?.checked == false)
        #expect(state.stories[r(2)]?.type.number == StoryType.text.number)
        #expect(state.stories[r(2)]?.checked == nil)
        #expect(state.stories[r(3)]?.type.number == StoryType.divider.number)
    }

    @Test func boxAndCardTagsToggle() {
        var state = manager.toggleTag(.box, positions: [r(1), r(2)], state: initial)
        #expect(state.stories[r(1)]?.hasTag("HIGH_LIGHT_BLOCK") == true)
        state = manager.toggleTag(.box, positions: [r(1)], state: state)
        #expect(state.stories[r(1)]?.hasTag("HIGH_LIGHT_BLOCK") == false)
        #expect(state.stories[r(2)]?.hasTag("HIGH_LIGHT_BLOCK") == true)
    }

    @Test func headingsReplaceEachOther() {
        var state = manager.toggleHeading(.h1, positions: [r(1)], state: initial)
        #expect(state.stories[r(1)]?.headingLevel == 1)
        state = manager.toggleHeading(.h3, positions: [r(1)], state: state)
        #expect(state.stories[r(1)]?.headingLevel == 3)
        #expect(state.stories[r(1)]?.tags.count == 1)
        state = manager.toggleHeading(.h3, positions: [r(1)], state: state)
        #expect(state.stories[r(1)]?.headingLevel == nil)
    }

    @Test func deleteKeepsTheTitle() {
        let state = manager.deleteSteps([r(0), r(1), r(3)], state: initial)
        #expect(state.sortedStories.map(\.id) == ["t", "b"])
    }

    @Test func textAndDocumentLinks() {
        #expect(manager.text(of: [r(2), r(1), r(3)], state: initial) == "One\nTwo")

        let state = manager.addDocumentLink(after: r(1), documentId: "p", title: "One", state: initial)
        // Between the first and the second step
        #expect(state.stories[b]?.type.number == StoryType.documentLink.number)
        #expect(state.stories[b]?.documentLink == DocumentLink(id: "p", title: "One"))
    }
}

@Suite struct PositionKeepingTests {
    init() { Rank.jitter = false }

    let manager = WriteopiaManager()

    /// title, a, b, c: "b" between the steps at 1 and 2.
    private func document() -> StoryState {
        var stories: [String: StoryStep] = [:]
        stories.place(StoryStep(id: "t", type: .title, text: "T", position: r(0)), at: r(0))
        stories.place(StoryStep(id: "a", type: .text, text: "a", position: r(0)), at: r(1))
        stories.place(StoryStep(id: "b", type: .text, text: "b", position: r(0)), at: b)
        stories.place(StoryStep(id: "c", type: .text, text: "c", position: r(0)), at: r(2))
        return StoryState(stories: stories)
    }

    @Test func neighboursComeFromTheKeys() {
        let stories = document().stories
        #expect(stories.previousKey(before: b) == r(1))
        #expect(stories.nextKey(after: b) == r(2))
        #expect(stories.nextKey(after: r(2)) == nil)
        #expect(stories.lastKey == r(2))
    }

    @Test func insertsTakeTheMiddleOrOneGapAfterTheLast() {
        var stories = document().stories
        #expect(stories.insert(StoryStep(type: .text, text: "x", position: r(0)), after: r(1)).key == mid(r(1), b))
        #expect(stories.insert(StoryStep(type: .text, text: "y", position: r(0)), after: r(2)).key == r(3))
        #expect(stories.insert(StoryStep(type: .text, text: "z", position: r(0)), before: b).key == mid(mid(r(1), b), b))
        #expect(StoryState(stories: stories).sortedStories.map(\.text) == ["T", "a", "x", "z", "b", "c", "y"])
    }

    @Test func insertingWithPlentyOfRoomGoesOneGapFurther() {
        var stories: [String: StoryStep] = [:]
        stories.place(StoryStep(type: .title, text: "T", position: r(0)), at: r(0))
        stories.place(StoryStep(type: .text, text: "far", position: r(0)), at: r(10))

        let insertion = stories.insert(
            [StoryStep(type: .text, text: "x", position: r(0)), StoryStep(type: .text, text: "y", position: r(0))],
            after: r(0)
        )

        #expect(insertion.keys == [r(1), r(2)])
    }

    @Test func insertingAgainAndAgainAtTheSameSpotNeverMovesTheOthers() {
        var stories: [String: StoryStep] = [:]
        stories.place(StoryStep(type: .title, text: "T", position: r(0)), at: r(0))
        stories.place(StoryStep(id: "a", type: .text, text: "a", position: r(0)), at: r(1))
        stories.place(StoryStep(id: "b", type: .text, text: "b", position: r(0)), at: r(2))

        for index in 0..<100 {
            stories.insert(StoryStep(type: .text, text: "\(index)", position: r(0)), after: r(1))
        }

        let state = StoryState(stories: stories)
        #expect(state.sortedStories.map(\.text) == ["T", "a"] + (0..<100).reversed().map(String.init) + ["b"])
        #expect(stories[r(1)]?.id == "a")
        #expect(stories[r(2)]?.id == "b")
        #expect(state.sortedStories.map(\.position) == state.sortedPositions)
    }

    @Test func aLineBreakGoesBetweenTheLineAndTheNextOne() {
        let initial = document()
        var step = initial.stories[b]!
        step.text = "b\n"

        let result = manager.onLineBreak(Action.LineBreak(storyStep: step, position: b, cursor: 2), state: initial)

        #expect(result.sortedStories.map(\.text) == ["T", "a", "b", "", "c"])
        #expect(result.sortedPositions == [r(0), r(1), b, mid(b, r(2)), r(2)])
        #expect(result.focus == mid(b, r(2)))
        #expect(result.stories[mid(b, r(2))]?.position == mid(b, r(2)))
    }

    @Test func stepsAddedAfterAStepSpreadUpToTheNextOne() {
        let initial = document()
        let result = manager.addSteps(
            [StoryStep(type: .text, text: "x", position: r(0)), StoryStep(type: .text, text: "y", position: r(0))],
            after: r(1),
            state: initial
        )

        #expect(result.sortedStories.map(\.text) == ["T", "a", "x", "y", "b", "c"])
        #expect(result.stories[r(1)]?.text == "a")
        #expect(result.stories[b]?.text == "b")
    }

    @Test func aStepAddedBeforeAnotherGoesHalfWayToThePrevious() {
        let result = manager.addStep(StoryStep(type: .text, text: "h", position: r(0)), before: b, state: document())

        #expect(result.sortedStories.map(\.text) == ["T", "a", "h", "b", "c"])
        #expect(result.stories[mid(r(1), b)]?.text == "h")
    }

    @Test func replacingAStepKeepsItsPositionForTheFirstReplacement() {
        let result = manager.replaceStep(
            id: "b",
            with: [StoryStep(type: .text, text: "b1", position: r(0)), StoryStep(type: .text, text: "b2", position: r(0))],
            state: document()
        )

        #expect(result.sortedStories.map(\.text) == ["T", "a", "b1", "b2", "c"])
        #expect(result.stories[b]?.text == "b1")
        #expect(result.stories[mid(b, r(2))]?.text == "b2")
        #expect(result.stories[r(2)]?.text == "c")
    }

    @Test func deletingKeepsThePositionsOfTheOthers() {
        let result = manager.deleteSteps([b], state: document())
        #expect(result.sortedPositions == [r(0), r(1), r(2)])

        let erased = manager.onErase(Action.EraseStory(storyStep: document().stories[r(2)]!, position: r(2)), state: document())
        #expect(erased.sortedPositions == [r(0), r(1), b])
        #expect(erased.stories[b]?.text == "bc")
    }

    @Test func loadingResolvesTwoStepsAtTheSamePosition() {
        let document = WrDocument(
            id: "d",
            title: "Plan",
            workspaceId: "w",
            content: [
                StoryStep(id: "t", type: .title, text: "Plan", position: r(0)),
                StoryStep(id: "x", type: .text, text: "x", position: r(1)),
                StoryStep(id: "y", type: .text, text: "y", position: r(1)),
                StoryStep(id: "z", type: .text, text: "z", position: r(2)),
            ]
        )

        let loaded = manager.loadDocument(document)

        #expect(loaded.sortedStories.map(\.id) == ["t", "x", "y", "z"])
        #expect(loaded.sortedPositions == [r(0), r(1), b, r(2)])
    }
}
