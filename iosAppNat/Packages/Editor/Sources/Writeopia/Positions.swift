import WrModels

/// Where steps were put: their keys, in order.
public struct Insertion: Equatable, Sendable {
    public var keys: [String]

    /// The key of the first (or only) step.
    public var key: String { keys[0] }
}

/// The position of a step is a sort key, not an index: the document is the steps read in key
/// order, and the key is also the position stored and synced. Keys are ranks (`Rank`): a step
/// added at the end goes to the next integer after the last one, a step added between two
/// others to a rank between them, which always exists. Nothing here ever renumbers another
/// step. The key is written into the step as well, so `step.position` and the key never
/// disagree. Mirrors `MapOperations` of the Kotlin SDK.
public extension Dictionary where Key == String, Value == StoryStep {
    /// The largest key, if any.
    var lastKey: String? { keys.max() }

    /// The largest key below `position`, if any.
    func previousKey(before position: String) -> String? {
        keys.reduce(nil) { best, key in key < position && (best == nil || key > best!) ? key : best }
    }

    /// The smallest key above `position`, if any.
    func nextKey(after position: String) -> String? {
        keys.reduce(nil) { best, key in key > position && (best == nil || key < best!) ? key : best }
    }

    /// Puts `step` at `key`, stored in the step as well.
    mutating func place(_ step: StoryStep, at key: String) {
        var step = step
        step.position = key
        self[key] = step
    }

    /// Puts `steps`, in order, right after `position` (which need not be a key): between it and
    /// the next key.
    @discardableResult
    mutating func insert(_ steps: [StoryStep], after position: String) -> Insertion {
        guard !steps.isEmpty else { return Insertion(keys: []) }

        let keys = Rank.between(position, nextKey(after: position), count: steps.count)
        for (step, key) in zip(steps, keys) { place(step, at: key) }
        return Insertion(keys: keys)
    }

    /// See `insert(_:after:)`.
    @discardableResult
    mutating func insert(_ step: StoryStep, after position: String) -> Insertion {
        insert([step], after: position)
    }

    /// Puts `steps`, in order, at `position` when it is free (the last of them takes it, the
    /// others go before), otherwise right before the step at `position`: between the previous
    /// key and that one.
    @discardableResult
    mutating func insert(_ steps: [StoryStep], before position: String) -> Insertion {
        guard !steps.isEmpty else { return Insertion(keys: []) }

        var before = steps
        var lastKeys: [String] = []
        if self[position] == nil {
            place(steps[steps.count - 1], at: position)
            before.removeLast()
            lastKeys = [position]
        }
        guard !before.isEmpty else { return Insertion(keys: lastKeys) }

        let keys = Rank.between(previousKey(before: position), position, count: before.count)
        for (step, key) in zip(before, keys) { place(step, at: key) }
        return Insertion(keys: keys + lastKeys)
    }

    /// See `insert(_:before:)`.
    @discardableResult
    mutating func insert(_ step: StoryStep, before position: String) -> Insertion {
        insert([step], before: position)
    }

    /// Puts `steps`, in order, at the end of the document, one integer apart.
    @discardableResult
    mutating func append(_ steps: [StoryStep]) -> [String] {
        var key = lastKey.map(Rank.after) ?? Rank.first
        var keys: [String] = []
        for step in steps {
            place(step, at: key)
            keys.append(key)
            key = Rank.after(key)
        }
        return keys
    }
}
