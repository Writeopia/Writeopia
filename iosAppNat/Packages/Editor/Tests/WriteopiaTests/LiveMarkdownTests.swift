import Testing
@testable import Writeopia
import WrModels

/// The same cases as `LiveMarkdownTest.kt`: both apps must read Markdown the same way.
@Suite struct LiveMarkdownTests {
    @Test func blocksOfEachKind() {
        let blocks = LiveMarkdown.blocks("""
        # Big
        ### Small
        ###### Smallest

        Some text
        - a bullet
        * another
        - [ ] to do
        - [x] done
        > quoted
        ---
        ![a cat](https://example.com/cat.png)
        1. numbered
        """)

        #expect(blocks.map(\.type) == [
            .text, .text, .text, .text, .unorderedListItem, .unorderedListItem,
            .checkItem, .checkItem, .text, .divider, .image, .text,
        ])
        #expect(blocks[0].tags == [TagInfo(tag: "H1")])
        #expect(blocks[1].tags == [TagInfo(tag: "H3")])
        #expect(blocks[2].tags == [TagInfo(tag: "H4")])
        #expect(blocks[2].text == "Smallest")
        #expect(blocks[4].text == "a bullet")
        #expect(blocks[6].checked == false)
        #expect(blocks[6].text == "to do")
        #expect(blocks[7].checked == true)
        #expect(blocks[8].tags == [TagInfo(tag: "HIGH_LIGHT_BLOCK")])
        #expect(blocks[8].text == "quoted")
        #expect(blocks[10].url == "https://example.com/cat.png")
        #expect(blocks[11].text == "1. numbered")
    }

    @Test func fencedCodeIsOneBlockPerLineKeptAsWritten() {
        let blocks = LiveMarkdown.blocks("```kotlin\nval a = **1**\n\n  b()\n```\nafter")

        #expect(blocks.map(\.type) == [.codeBlock, .codeBlock, .codeBlock, .text])
        #expect(blocks.map(\.text) == ["val a = **1**", "", "  b()", "after"])
    }

    @Test func equationsOnOneLineAndOverSeveral() {
        let blocks = LiveMarkdown.blocks("$$ e = mc^2 $$\n$$\n\\frac{a}{b}\n$$")

        #expect(blocks.map(\.text) == ["e = mc^2", "\\frac{a}{b}"])
        #expect(blocks[1].type == .equation)
    }

    @Test func inlineMarkersBecomeSpans() {
        let (text, spans) = LiveMarkdown.inline("A **bold** and *italic* and _this_ and ==marked== word")

        #expect(text == "A bold and italic and this and marked word")
        #expect(Set(spans) == [
            SpanInfo(start: 2, end: 6, span: "BOLD"),
            SpanInfo(start: 11, end: 17, span: "ITALIC"),
            SpanInfo(start: 22, end: 26, span: "ITALIC"),
            SpanInfo(start: 31, end: 37, span: "HIGHLIGHT"),
        ])
    }

    @Test func linksWithALabelAndBareAddresses() {
        let (text, spans) = LiveMarkdown.inline("See [the docs](https://a.io/x) or https://b.io.")

        #expect(text == "See the docs or https://b.io.")
        #expect(Set(spans) == [
            SpanInfo(start: 4, end: 12, span: "LINK", extra: "https://a.io/x"),
            SpanInfo(start: 16, end: 28, span: "LINK", extra: "https://b.io"),
        ])
    }

    @Test func markersThatNeverCloseStayAsText() {
        #expect(LiveMarkdown.inline("2 * 3 = 6 and snake_case_name") == ("2 * 3 = 6 and snake_case_name", []))
        #expect(LiveMarkdown.inline("**not bold") == ("**not bold", []))
    }

    @Test func codeAndEscapesAreKeptAsText() {
        #expect(LiveMarkdown.inline("call `f(**x**)` and \\*star\\*") == ("call f(**x**) and *star*", []))
    }

    @Test func nestedBoldAndItalic() {
        let (text, spans) = LiveMarkdown.inline("**bold *both***")

        #expect(text == "bold both")
        #expect(Set(spans) == [SpanInfo(start: 5, end: 9, span: "ITALIC"), SpanInfo(start: 0, end: 9, span: "BOLD")])
    }

    @Test func offsetsAreUtf16LikeTheEditor() {
        let (text, spans) = LiveMarkdown.inline("😀 **ok**")

        #expect(text == "😀 ok")
        #expect(spans == [SpanInfo(start: 3, end: 5, span: "BOLD")])
    }
}
