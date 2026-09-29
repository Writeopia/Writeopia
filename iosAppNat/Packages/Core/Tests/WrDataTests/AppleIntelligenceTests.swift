import Foundation
import Testing
@testable import WrData
import WrModels

@Suite struct AiPromptsTests {
    @Test func commandsWrapTheTextLikeTheBackend() {
        let summary = AiPrompts.prompt(for: .summary, text: "Some notes")
        #expect(summary.hasPrefix("Summarize the following text"))
        #expect(summary.hasSuffix(":\n```\nSome notes\n```"))

        #expect(AiPrompts.prompt(for: .actionPoints, text: "x").hasPrefix("Extract key action points"))
        #expect(AiPrompts.prompt(for: .faq, text: "x").hasPrefix("Generate a list of frequently asked questions"))
        #expect(AiPrompts.prompt(for: .tags, text: "x").hasPrefix("Generate a list of relevant tags"))
    }

    @Test func freePromptIsSentAsIs() {
        #expect(AiPrompts.prompt(for: .prompt, text: "Write a haiku about notes") == "Write a haiku about notes")
    }

    @Test func shortTextIsOneChunk() {
        #expect(AiPrompts.chunks(of: "Short text", limit: 100) == ["Short text"])
    }

    @Test func longTextIsCutBetweenLinesWithinTheLimit() {
        let lines = (1...20).map { "Line number \($0) of the document" }
        let text = lines.joined(separator: "\n")

        let chunks = AiPrompts.chunks(of: text, limit: 100)

        #expect(chunks.count > 1)
        #expect(chunks.allSatisfy { $0.count <= 100 })
        // Nothing is lost and no line is cut in the middle.
        #expect(chunks.joined(separator: "\n") == text)
    }

    @Test func aLineLongerThanTheLimitIsCutBetweenWords() {
        let text = Array(repeating: "word", count: 50).joined(separator: " ")
        let chunks = AiPrompts.chunks(of: text, limit: 32)

        #expect(chunks.allSatisfy { $0.count <= 32 })
        #expect(chunks.joined(separator: " ") == text)
    }

    @Test func aWordLongerThanTheLimitIsSplit() {
        let chunks = AiPrompts.chunks(of: String(repeating: "a", count: 25), limit: 10)
        #expect(chunks == ["aaaaaaaaaa", "aaaaaaaaaa", "aaaaa"])
    }

    @Test func statusMessagesExplainWhatToDo() {
        #expect(AppleIntelligenceStatus.available.isAvailable)
        #expect(!AppleIntelligenceStatus.notEnabled.isAvailable)
        #expect(AppleIntelligenceStatus.notEnabled.message.contains("Settings"))
        #expect(AppleIntelligenceStatus.unsupportedSystem.message.contains("iOS 26"))
    }

    @Test func errorsBecomeMessagesForTheAnswer() {
        struct Failure: LocalizedError {
            var errorDescription: String? { "Broken" }
        }
        #expect(AppleIntelligenceAi.mapped(Failure()) as? AiStreamError == AiStreamError(message: "Broken"))
        #expect(AppleIntelligenceAi.mapped(CancellationError()) is CancellationError)
    }
}

@Suite struct MarkdownStepsTests {
    @Test func readsHeadingsListsAndParagraphs() {
        let steps = MarkdownSteps.parse("""
            # Title
            ## Section
            Some **bold** text
            - Item
            - [ ] Todo
            - [x] Done
            ```
            """)

        #expect(steps.map(\.text) == ["Title", "Section", "Some bold text", "Item", "Todo", "Done"])
        #expect(steps[0].hasTag("H1"))
        #expect(steps[1].hasTag("H2"))
        #expect(steps[3].type == .unorderedListItem)
        #expect(steps[4].type == .checkItem && steps[4].checked == false)
        #expect(steps[5].type == .checkItem && steps[5].checked == true)
        #expect(steps.map(\.position) == [1, 2, 3, 4, 5, 6])
    }

    @Test func listItemsIgnoreTheTextAroundTheList() {
        let answer = """
            Here are some options:
            - Milk
            * **Eggs**
            1. Bread
            - [ ] Butter
            Hope it helps
            """
        #expect(MarkdownSteps.listItems(answer, limit: 5) == ["Milk", "Eggs", "Bread", "Butter"])
        #expect(MarkdownSteps.listItems(answer, limit: 2) == ["Milk", "Eggs"])
    }
}

/// Runs the real on-device model. Opt-in (`WR_LIVE_AI=1 swift test`) because it needs a Mac
/// with Apple Intelligence turned on and takes a few seconds.
@Suite(.enabled(if: ProcessInfo.processInfo.environment["WR_LIVE_AI"] == "1"))
struct AppleIntelligenceLiveTests {
    @Test func summaryStreamsFromTheDeviceModel() async throws {
        try #require(AppleIntelligenceAi.isAvailable, "\(AppleIntelligenceAi.status.message)")

        var answers: [String] = []
        let text = "Meeting notes. We agreed to ship the iOS app next month. Ana will write the release notes. Bruno will fix the sync bug before Friday."
        for try await partial in AppleIntelligenceAi().stream(.actionPoints, prompt: text) {
            answers.append(partial)
        }

        let answer = try #require(answers.last)
        print("Apple Intelligence answer:\n\(answer)")
        #expect(!answer.isEmpty)
        // Each element is the whole answer so far, like the cloud AI.
        #expect(zip(answers, answers.dropFirst()).allSatisfy { $1.hasPrefix($0) || $1.count >= $0.count })
    }

    @Test func longTextIsCondensedBeforeTheCommand() async throws {
        try #require(AppleIntelligenceAi.isAvailable)

        let paragraph = "The team discussed the roadmap of the note taking app, the sync engine and the new editor. "
        let text = String(repeating: paragraph, count: 30)
        var answer = ""
        for try await partial in AppleIntelligenceAi(inputLimit: 1200).stream(.summary, prompt: text) {
            answer = partial
        }
        print("Condensed summary:\n\(answer)")
        #expect(!answer.isEmpty)
    }
}
