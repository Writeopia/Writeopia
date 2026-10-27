import Testing
@testable import Writeopia
import WrModels

private func state(_ steps: [StoryStep]) -> StoryState {
    StoryState(stories: Dictionary(uniqueKeysWithValues: steps.map { ($0.position, $0) }))
}

@Suite struct LinkDetectorTests {
    @Test func recognizesWebAddresses() {
        #expect(LinkDetector.isLink("https://writeopia.io"))
        #expect(LinkDetector.isLink("http://example.com/path?query=1#frag"))
        #expect(LinkDetector.isLink("  https://www.google.com/  "))
        #expect(LinkDetector.isLink("HTTPS://WRITEOPIA.IO"))
    }

    @Test func rejectsTextThatIsNotOnlyAnAddress() {
        #expect(!LinkDetector.isLink(""))
        #expect(!LinkDetector.isLink("writeopia.io"))
        #expect(!LinkDetector.isLink("https://"))
        #expect(!LinkDetector.isLink("see https://writeopia.io"))
        #expect(!LinkDetector.isLink("https://writeopia.io and more"))
        #expect(!LinkDetector.isLink("ftp://files.example.com"))
    }

    @Test func aPasteIsAWholeLinkArrivingInAnEmptyLine() {
        #expect(LinkDetector.isPastedLink(previous: nil, new: "https://writeopia.io"))
        #expect(LinkDetector.isPastedLink(previous: "", new: "https://writeopia.io"))
        #expect(!LinkDetector.isPastedLink(previous: "h", new: "ht"))
        #expect(!LinkDetector.isPastedLink(previous: "see ", new: "see https://writeopia.io"))
    }
}

@Suite struct LinkCardCreationTests {
    private let manager = WriteopiaManager()

    @Test func theLastEmptyLineBecomesACardAndATextLineIsAddedBelow() {
        let initial = state([
            StoryStep(id: "t", type: .title, text: "Title", position: Rank.at(0)),
            StoryStep(id: "a", type: .text, text: "", position: Rank.at(1)),
        ])

        let result = manager.createLinkCard(url: "https://writeopia.io", at: Rank.at(1), state: initial)

        let card = result.stories[Rank.at(1)]!
        #expect(card.id == "a")
        #expect(card.type == .linkCard)
        #expect(card.url == "https://writeopia.io")
        #expect(card.text == nil)
        #expect(result.sortedStories.count == 3)
        // The new line goes one integer after the card, the last step
        #expect(result.stories[Rank.at(2)]?.type == .text)
        #expect(result.stories[Rank.at(2)]?.text == "")
        #expect(result.focus == Rank.at(2))
        #expect(result.lastEdit == .whole)
    }

    @Test func anExistingTextLineBelowTakesTheCursor() {
        let initial = state([
            StoryStep(id: "t", type: .title, text: "Title", position: Rank.at(0)),
            StoryStep(id: "a", type: .text, text: "", position: Rank.at(1)),
            StoryStep(id: "b", type: .text, text: "below", position: Rank.at(2)),
        ])

        let result = manager.createLinkCard(url: "https://writeopia.io", at: Rank.at(1), state: initial)

        #expect(result.stories[Rank.at(1)]?.type == .linkCard)
        #expect(result.sortedStories.count == 3)
        #expect(result.stories[Rank.at(2)]?.text == "below")
        #expect(result.focus == Rank.at(2))
    }

    @Test func markdownExportsTheCardAsALink() {
        let withDescription = StoryStep(type: .linkCard, text: "A text editor.", url: "https://writeopia.io", position: Rank.at(1))
        let bare = StoryStep(type: .linkCard, url: "https://writeopia.io", position: Rank.at(1))
        #expect(DocumentToMarkdown.parse([withDescription]) == "[A text editor.](https://writeopia.io)\n")
        #expect(DocumentToMarkdown.parse([bare]) == "[https://writeopia.io](https://writeopia.io)\n")

        // Brackets in the label and spaces or parentheses in the address can not break the link.
        let brackets = StoryStep(type: .linkCard, text: "Editor [beta] done", url: "https://writeopia.io", position: Rank.at(1))
        let oddAddress = StoryStep(type: .linkCard, text: "Odd address", url: "https://w.io/a b(c)", position: Rank.at(1))
        #expect(DocumentToMarkdown.parse([brackets]) == "[Editor \\[beta\\] done](https://writeopia.io)\n")
        #expect(DocumentToMarkdown.parse([oddAddress]) == "[Odd address](<https://w.io/a b(c)>)\n")
    }
}
