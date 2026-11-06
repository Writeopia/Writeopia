import Foundation
import Testing
import WrModels

@Suite struct SearchTermsTests {
    @Test func wordsAreLowercasedAndTheRestDropped() {
        #expect(SearchTerms("Hello, WORLD!").tokens == ["hello", "world"])
        #expect(SearchTerms("foo:*&|!() 'bar\"").tokens == ["foo", "bar"])
        #expect(SearchTerms("café über 日本 42").tokens == ["café", "über", "日本", "42"])
        #expect(SearchTerms("   ").isEmpty)
        #expect(SearchTerms("!!! ---").isEmpty)
    }

    @Test func theNumberAndTheLengthOfTheWordsAreCapped() {
        let many = (1...20).map { "w\($0)" }.joined(separator: " ")
        #expect(SearchTerms(many).tokens.count == SearchTerms.maxTokens)
        #expect(SearchTerms(String(repeating: "a", count: 500)).tokens.first?.count == SearchTerms.maxTokenLength)
    }

    @Test func aTextMatchesWhenItHoldsEveryWordByPrefix() {
        let terms = SearchTerms("quart REPORT")
        #expect(terms.matches("The Quarterly report was approved"))
        #expect(!terms.matches("quarterly numbers only"))
        #expect(!SearchTerms("rocer").matches("Groceries"))
        #expect(!SearchTerms("").matches("anything"))
    }

    @Test func wordsOccurInsideOtherWordsForTheHighlights() {
        let terms = SearchTerms("para")
        #expect(terms.occurs(in: "Separar as roupas"))
        #expect(!terms.matches("Separar as roupas"))
        #expect(terms.ranges(in: "Separar para").map(\.location) == [2, 8])
        #expect(SearchTerms("fox the").ranges(in: "The quick brown fox jumps over the lazy dog").map(\.location) == [0, 16, 31])
    }

    @Test func theSnippetIsCutAroundTheFirstWordFound() {
        let terms = SearchTerms("zebra")
        #expect(terms.snippet(of: "buy some\nbananas") == "buy some bananas")

        let long = String(repeating: "a", count: 200) + " the zebrafish samples arrived " + String(repeating: "b", count: 200)
        let snippet = terms.snippet(of: long, maxLength: 60)
        #expect(snippet.hasPrefix("…") && snippet.hasSuffix("…"))
        #expect(snippet.contains("zebrafish"))
        #expect(snippet.count <= 62)

        #expect(SearchTerms("missing").snippet(of: String(repeating: "x", count: 200), maxLength: 50) == String(repeating: "x", count: 50) + "…")
    }
}
