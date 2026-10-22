import Foundation

/// The position of a step as a sort key that always has room between two neighbours, so no
/// step ever has to move to make room for another (fractional indexing, like LexoRank without
/// its buckets). Mirrors `Rank` of the Kotlin SDK: the two must produce the same keys.
///
/// A rank is an integer part of ten decimal digits, zero padded, followed by an optional
/// fraction in base 36 (`0-9a-z`) with no trailing `0`. Ranks compare as plain strings, byte by
/// byte: `Rank.first` is `"0001000000"`, the step after it `"0001000001"`, and a step between the
/// two `"0001000000i"`. A short random suffix goes on every key made between two others, so two
/// devices inserting between the same steps don't produce the same key.
public enum Rank {
    private static let integerWidth = 10
    private static let firstInteger = 1_000_000
    private static let maxInteger = 9_999_999_999
    private static let digits = Array("0123456789abcdefghijklmnopqrstuvwxyz")
    private static let jitterLength = 2

    /// The position of the first step of a new document.
    public static let first = "0001000000"

    /// Below every rank; never a rank itself (the integer part always has ten digits).
    public static let none = ""

    /// Whether keys made between two others get a random suffix. On by default; tests that
    /// assert exact keys turn it off.
    nonisolated(unsafe) public static var jitter = true

    /// The rank of the `index`th step of a document written in order: `first` for 0.
    public static func at(_ index: Int) -> String { integer(firstInteger + index) }

    /// The rank right after `rank`: the next integer.
    public static func after(_ rank: String) -> String {
        let next = integerPart(rank) + 1
        return next <= maxInteger ? integer(next) : integer(integerPart(rank)) + midpoint(fractionPart(rank), nil)
    }

    /// The rank right before `rank`: its integer alone when it has a fraction, otherwise the
    /// previous integer.
    public static func before(_ rank: String) -> String {
        let value = integerPart(rank)
        if !fractionPart(rank).isEmpty { return integer(value) }
        precondition(value > 0, "No rank before \(rank)")
        return integer(value - 1)
    }

    /// A rank strictly between `after` and `before` (nil for no bound on that side). When the
    /// integers leave room, the integer right after `after`; otherwise the midpoint of the
    /// fractions, with a random suffix when `jitter` is on.
    public static func between(_ after: String?, _ before: String?) -> String {
        between(after, before, jitter: jitter)
    }

    /// `between(_:_:)` with the random suffix on or off whatever `Rank.jitter` says, e.g. for a
    /// test to know the key the code makes with the suffix off.
    public static func between(_ after: String?, _ before: String?, jitter: Bool) -> String {
        guard let after else { return before.map(Self.before) ?? first }
        guard let before else { return Self.after(after) }
        precondition(after < before, "Ranks out of order: \(after) >= \(before)")

        let integerAfter = integerPart(after)
        let integerBefore = integerPart(before)
        let key: String
        if integerBefore > integerAfter + 1 {
            key = integer(integerAfter + 1)
        } else if integerBefore == integerAfter + 1 {
            key = integer(integerAfter) + midpoint(fractionPart(after), nil)
        } else {
            key = integer(integerAfter) + midpoint(fractionPart(after), fractionPart(before))
        }
        return jitter ? jittered(key, before: before) : key
    }

    /// `count` ranks in order, each between the one before it and `before`.
    public static func between(_ after: String?, _ before: String?, count: Int) -> [String] {
        var previous = after
        return (0..<max(count, 0)).map { _ in
            let key = between(previous, before)
            previous = key
            return key
        }
    }

    private static func jittered(_ key: String, before: String) -> String {
        var suffix = ""
        for _ in 0..<(jitterLength - 1) { suffix.append(digits[Int.random(in: 0..<digits.count)]) }
        suffix.append(digits[1 + Int.random(in: 0..<(digits.count - 1))]) // never a trailing 0
        let candidate = key + suffix
        return candidate < before ? candidate : key
    }

    private static func integerPart(_ rank: String) -> Int { Int(rank.prefix(integerWidth)) ?? 0 }

    private static func fractionPart(_ rank: String) -> String { String(rank.dropFirst(integerWidth)) }

    private static func integer(_ value: Int) -> String {
        precondition(value >= 0 && value <= maxInteger, "Rank integer out of range: \(value)")
        let text = String(value)
        return String(repeating: "0", count: max(integerWidth - text.count, 0)) + text
    }

    /// A fraction strictly between `a` and `b` (nil for the top), both without trailing `0`.
    /// The classic fractional-indexing midpoint over base-36 digits.
    private static func midpoint(_ a: String, _ b: String?) -> String {
        let aChars = Array(a)
        if let b {
            precondition(a < b, "Fractions out of order: \(a) >= \(b)")
            let bChars = Array(b)
            var common = 0
            while common < bChars.count, (common < aChars.count ? aChars[common] : "0") == bChars[common] { common += 1 }
            if common > 0 {
                return String(bChars.prefix(common)) + midpoint(String(aChars.dropFirst(common)), String(bChars.dropFirst(common)))
            }
        }

        let digitA = aChars.first.flatMap { digits.firstIndex(of: $0) } ?? 0
        let digitB = b?.first.flatMap { digits.firstIndex(of: $0) } ?? digits.count
        if digitB - digitA > 1 {
            return String(digits[(digitA + digitB) / 2])
        } else if let b, b.count > 1 {
            return String(b.prefix(1))
        } else {
            return String(digits[digitA]) + midpoint(String(aChars.dropFirst()), nil)
        }
    }
}
