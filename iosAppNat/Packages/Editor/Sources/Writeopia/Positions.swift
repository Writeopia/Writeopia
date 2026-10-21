import WrModels

/// When steps are spread out again, at least this far apart when there is room for it.
private let preferredRoom = positionGap / 8

/// At most this many steps are spread out again at once, when there is room before them.
private let maxRespaced = 32

/// Where steps were put: their keys, in order, and the new keys of the steps moved to make
/// room for them (their old keys are gone from the dictionary).
public struct Insertion: Equatable, Sendable {
    public var keys: [Int]
    public var moved: [Int]

    /// The key of the first (or only) step.
    public var key: Int { keys[0] }
}

/// The position of a step is a sort key, not an index: the document is the steps read in key
/// order, and the key is also the position stored and synced. Keys are whole numbers,
/// `positionGap` apart when the document is written in order: a step added at the end goes one
/// gap after the last one, a step added between two others goes half way between them. Nothing
/// here renumbers the whole document: when two neighbours have no whole number left between
/// them, the few steps after the gap are spread out again (`makeRoom(after:count:)`) and
/// reported, so the caller saves them. The key is written into the step as well, so
/// `step.position` and the key never disagree. Mirrors `MapOperations` of the Kotlin SDK.
public extension Dictionary where Key == Int, Value == StoryStep {
    /// The largest key, if any.
    var lastKey: Int? { keys.max() }

    /// The largest key below `position`, if any.
    func previousKey(before position: Int) -> Int? {
        keys.reduce(nil) { best, key in key < position && (best == nil || key > best!) ? key : best }
    }

    /// The smallest key above `position`, if any.
    func nextKey(after position: Int) -> Int? {
        keys.reduce(nil) { best, key in key > position && (best == nil || key < best!) ? key : best }
    }

    /// Keys for `count` steps between `position` (excluded) and `next` (excluded, none when
    /// nil), in order. With plenty of room they go one gap apart from `position`, like steps
    /// written in order; otherwise they are spread evenly. There must be room for them.
    private static func keysBetween(_ position: Int, _ next: Int?, count: Int) -> [Int] {
        guard count > 0 else { return [] }
        let step: Int
        if let next {
            step = next - position > (count + 1) * positionGap ? positionGap : (next - position) / (count + 1)
        } else {
            step = positionGap
        }
        return (1...count).map { position + $0 * step }
    }

    /// Whether `count` whole numbers fit between `position` and `next` (none when nil).
    private static func hasRoom(after position: Int, before next: Int?, count: Int) -> Bool {
        guard let next else { return true }
        return (next - position) / (count + 1) >= 1
    }

    /// Spreads out the steps after `anchor` so that `count` new ones fit between `anchor` and
    /// the first of them, in place. As few steps as possible move: the ones right after
    /// `anchor`, until the room allows at least `preferredRoom` between each, or up to
    /// `maxRespaced` of them when a smaller room does.
    ///
    /// - Returns: the new keys of the steps moved, in order; empty when nothing had to move.
    @discardableResult
    mutating func makeRoom(after anchor: Int, count: Int) -> [Int] {
        var taken: [Int] = []
        var next = nextKey(after: anchor)
        var step: Int

        while true {
            let slots = taken.count + count + 1
            guard let current = next else {
                step = positionGap
                break
            }
            step = (current - anchor) / slots
            if step >= 1, step >= preferredRoom || taken.count >= maxRespaced { break }
            taken.append(current)
            next = nextKey(after: current)
        }

        guard !taken.isEmpty else { return [] }

        let steps = taken.map { removeValue(forKey: $0)! }
        let newKeys = taken.indices.map { anchor + (count + $0 + 1) * step }
        for (index, moved) in steps.enumerated() { place(moved, at: newKeys[index]) }
        return newKeys
    }

    /// Puts `step` at `key`, stored in the step as well.
    mutating func place(_ step: StoryStep, at key: Int) {
        var step = step
        step.position = key
        self[key] = step
    }

    /// Puts `steps`, in order, right after `position` (which need not be a key), spreading
    /// the following steps out first when there is no room for them.
    @discardableResult
    mutating func insert(_ steps: [StoryStep], after position: Int) -> Insertion {
        guard !steps.isEmpty else { return Insertion(keys: [], moved: []) }

        let moved = Self.hasRoom(after: position, before: nextKey(after: position), count: steps.count)
            ? []
            : makeRoom(after: position, count: steps.count)
        let keys = Self.keysBetween(position, nextKey(after: position), count: steps.count)
        for (step, key) in zip(steps, keys) { place(step, at: key) }
        return Insertion(keys: keys, moved: moved)
    }

    /// See `insert(_:after:)`.
    @discardableResult
    mutating func insert(_ step: StoryStep, after position: Int) -> Insertion {
        insert([step], after: position)
    }

    /// Puts `steps`, in order, at `position` when it is free (the last of them takes it, the
    /// others go before), otherwise right before the step at `position`: between the previous
    /// step and that one, spreading the following steps out first when there is no room.
    @discardableResult
    mutating func insert(_ steps: [StoryStep], before position: Int) -> Insertion {
        guard !steps.isEmpty else { return Insertion(keys: [], moved: []) }

        var before = steps
        var lastKeys: [Int] = []
        if self[position] == nil {
            place(steps[steps.count - 1], at: position)
            before.removeLast()
            lastKeys = [position]
        }
        guard !before.isEmpty else { return Insertion(keys: lastKeys, moved: []) }

        guard let previous = previousKey(before: position) else {
            // Nothing before: the steps go one gap apart, up to `position`
            let keys = before.indices.map { position - (before.count - $0) * positionGap }
            for (step, key) in zip(before, keys) { place(step, at: key) }
            return Insertion(keys: keys + lastKeys, moved: [])
        }

        // The step at `position` moves too, with the ones after it, when there is no room
        let moved = Self.hasRoom(after: previous, before: position, count: before.count)
            ? []
            : makeRoom(after: previous, count: before.count)
        let keys = Self.keysBetween(previous, nextKey(after: previous), count: before.count)
        for (step, key) in zip(before, keys) { place(step, at: key) }
        return Insertion(keys: keys + lastKeys, moved: moved)
    }

    /// See `insert(_:before:)`.
    @discardableResult
    mutating func insert(_ step: StoryStep, before position: Int) -> Insertion {
        insert([step], before: position)
    }

    /// Puts `steps`, in order, at the end of the document, one gap apart.
    @discardableResult
    mutating func append(_ steps: [StoryStep]) -> [Int] {
        var key = lastKey.map { $0 + positionGap } ?? 0
        var keys: [Int] = []
        for step in steps {
            place(step, at: key)
            keys.append(key)
            key += positionGap
        }
        return keys
    }
}
