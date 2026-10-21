import WrModels

/// The position of a step is a sort key, not an index: the document is the steps read in key
/// order, and the key is also the position stored and synced. Nothing here moves another step:
/// a new step goes to a free key between its neighbours (1.5 between 1 and 2), and the key is
/// written into the step as well, so `step.position` and the key never disagree. Mirrors
/// `MapOperations` of the Kotlin SDK.
public extension Dictionary where Key == Double, Value == StoryStep {
    /// The largest key, if any.
    var lastKey: Double? { keys.max() }

    /// The largest key below `position`, if any.
    func previousKey(before position: Double) -> Double? {
        keys.reduce(nil) { best, key in key < position && (best == nil || key > best!) ? key : best }
    }

    /// The smallest key above `position`, if any.
    func nextKey(after position: Double) -> Double? {
        keys.reduce(nil) { best, key in key > position && (best == nil || key < best!) ? key : best }
    }

    /// A key for a step placed before whatever is at `position`: `position` itself when free,
    /// otherwise half way to the previous key (or one below when there is nothing before).
    func keyBefore(_ position: Double) -> Double {
        guard self[position] != nil else { return position }
        guard let previous = previousKey(before: position) else { return position - 1 }
        return (previous + position) / 2
    }

    /// A key for a step placed right after whatever is at `position`: half way to the next key,
    /// or one above when it is the last.
    func keyAfter(_ position: Double) -> Double {
        guard let next = nextKey(after: position) else { return position + 1 }
        return (position + next) / 2
    }

    /// `count` free keys right after `position`, in order, spread evenly up to the next key.
    func keysAfter(_ position: Double, count: Int) -> [Double] {
        guard count > 0 else { return [] }
        guard let next = nextKey(after: position) else {
            return (1...count).map { position + Double($0) }
        }
        let gap = (next - position) / Double(count + 1)
        return (1...count).map { position + Double($0) * gap }
    }

    /// Puts `step` at `key`, stored in the step as well.
    mutating func place(_ step: StoryStep, at key: Double) {
        var step = step
        step.position = key
        self[key] = step
    }

    /// Puts `steps`, in order, right after `position`.
    ///
    /// - Returns: the keys they got.
    @discardableResult
    mutating func place(_ steps: [StoryStep], after position: Double) -> [Double] {
        let keys = keysAfter(position, count: steps.count)
        for (step, key) in zip(steps, keys) { place(step, at: key) }
        return keys
    }

    /// Puts `steps`, in order, at the end of the document.
    ///
    /// - Returns: the keys they got.
    @discardableResult
    mutating func append(_ steps: [StoryStep]) -> [Double] {
        var key = lastKey.map { $0 + 1 } ?? 0
        var keys: [Double] = []
        for step in steps {
            place(step, at: key)
            keys.append(key)
            key += 1
        }
        return keys
    }
}
