import Foundation
import Testing
import WrModels
@testable import WriteopiaUI

@Suite struct TextStylesTests {
    @Test func linkSpansCarryTheirURL() {
        let step = StoryStep(
            id: "a", type: .text, text: "Visit writeopia.io today",
            spans: [SpanInfo(start: 6, end: 17, span: "LINK", extra: "https://writeopia.io")], position: Rank.at(0)
        )
        let text = TextStyles.attributedText(for: step)

        var range = NSRange()
        let url = text.attribute(.link, at: 6, effectiveRange: &range) as? URL
        #expect(url == URL(string: "https://writeopia.io"))
        #expect(range == NSRange(location: 6, length: 11))
        #expect(text.attribute(.link, at: 0, effectiveRange: nil) == nil)
    }

    @Test func linksWithoutSchemeOpenOverHttps() {
        #expect(TextStyles.linkURL("writeopia.io") == URL(string: "https://writeopia.io"))
        #expect(TextStyles.linkURL(" http://a.b ") == URL(string: "http://a.b"))
        #expect(TextStyles.linkURL("localhost:8080") == URL(string: "https://localhost:8080"))
        #expect(TextStyles.linkURL("") == nil)
        #expect(TextStyles.linkURL(nil) == nil)
    }

    @Test func explicitSchemesAreKept() {
        #expect(TextStyles.linkURL("mailto:hi@writeopia.io") == URL(string: "mailto:hi@writeopia.io"))
        #expect(TextStyles.linkURL("tel:+5511999999999") == URL(string: "tel:+5511999999999"))
        #expect(LinkInput.normalized("mailto:hi@writeopia.io") == "mailto:hi@writeopia.io")
        #expect(LinkInput.normalized("localhost:8080") == "https://localhost:8080")
        #expect(LinkInput.normalized("writeopia.io:443/path") == "https://writeopia.io:443/path")
    }

    @Test func linkSpansWithoutURLAreOnlyStyled() {
        let step = StoryStep(
            id: "a", type: .text, text: "no url",
            spans: [SpanInfo(start: 0, end: 6, span: "LINK", extra: nil)], position: Rank.at(0)
        )
        let text = TextStyles.attributedText(for: step)
        #expect(text.attribute(.link, at: 0, effectiveRange: nil) == nil)
        #expect(text.attribute(.underlineStyle, at: 0, effectiveRange: nil) != nil)
    }
}
