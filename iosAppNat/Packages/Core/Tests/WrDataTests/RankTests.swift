import Foundation
import Testing
import WrModels

/// Mirrors `RankTest` of the Kotlin SDK: the two must make the same keys.
@Suite struct RankTests {
    @Test func ranksOfAnOrderedDocumentAreOneIntegerApart() {
        #expect(Rank.at(0) == Rank.first)
        #expect(Rank.at(0) == "0001000000")
        #expect(Rank.at(1) == "0001000001")
        #expect(Rank.after(Rank.at(1)) == Rank.at(2))
        #expect(Rank.before(Rank.at(1)) == Rank.at(0))
        #expect(Rank.before("0001000001i") == Rank.at(1))
    }

    @Test func betweenTwoIntegersGoesToTheMiddleOfTheFraction() {
        #expect(Rank.between(Rank.at(1), Rank.at(2), jitter: false) == "0001000001i")
        #expect(Rank.between(Rank.at(1), "0001000001i", jitter: false) == "00010000019")
        #expect(Rank.between("0001000001i", Rank.at(2), jitter: false) == "0001000001r")
        #expect(Rank.between(Rank.at(1), Rank.at(5), jitter: false) == Rank.at(2))
        #expect(Rank.between(nil, nil, jitter: false) == Rank.first)
        #expect(Rank.between(Rank.at(3), nil, jitter: false) == Rank.at(4))
        #expect(Rank.between(nil, Rank.at(3), jitter: false) == Rank.at(2))
    }

    @Test func thereIsAlwaysRoomBetweenTwoRanks() {
        for jitter in [false, true] {
            var low = Rank.at(1)
            let high = Rank.at(2)
            // Always inserting right after the same step: the worst case for key length
            for _ in 0..<200 {
                let key = Rank.between(low, high, jitter: jitter)
                #expect(low < key && key < high)
                #expect(key.last != "0")
                low = key
            }
            var top = Rank.at(2)
            for _ in 0..<200 {
                let key = Rank.between(Rank.at(1), top, jitter: jitter)
                #expect(Rank.at(1) < key && key < top)
                #expect(key.last != "0")
                top = key
            }
        }
    }

    @Test func manyRanksBetweenTwoAreInOrder() {
        let keys = Rank.between(Rank.at(1), Rank.at(2), count: 5)
        #expect(keys == keys.sorted())
        #expect(keys.allSatisfy { Rank.at(1) < $0 && $0 < Rank.at(2) })
        #expect(Set(keys).count == 5)
    }

    @Test func aNumericPositionFromAnOldDocumentIsReadAsTheRankAtThatNumber() throws {
        let decoder = JSONDecoder()
        let rank = try decoder.decode(StoryStep.self, from: Data(#"{"id":"a","type":{"name":"message","number":0},"position":"0001000003"}"#.utf8))
        let whole = try decoder.decode(StoryStep.self, from: Data(#"{"id":"a","type":{"name":"message","number":0},"position":2}"#.utf8))
        let fraction = try decoder.decode(StoryStep.self, from: Data(#"{"id":"a","type":{"name":"message","number":0},"position":1.5}"#.utf8))
        #expect(rank.position == "0001000003")
        #expect(whole.position == Rank.at(2))
        #expect(fraction.position == Rank.at(1))
    }
}
