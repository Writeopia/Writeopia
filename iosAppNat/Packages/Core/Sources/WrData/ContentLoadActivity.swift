import Observation

/// Tells whether content picked in the side menu is being loaded: a document opening in the
/// editor (read from the device, then fetched from the backend and merged) or a folder
/// expanding (its children fetched from the backend). The side menu shows a spinner by its
/// folders while it is, so the person knows what they see may still change. Counterpart of
/// `ContentLoadActivity` of the Compose app.
///
/// Loads may overlap (an editor opening while a folder still expands), so the running ones
/// are counted: `isLoading` only goes back to false once the last one finishes.
@Observable
public final class ContentLoadActivity {
    public private(set) var isLoading = false
    @ObservationIgnored private var running = 0

    public init() {}

    /// Runs `body` as a content load: `isLoading` holds true until it returns or throws.
    public func track<T>(_ body: () async throws -> T) async rethrows -> T {
        running += 1
        isLoading = true
        defer {
            running -= 1
            isLoading = running > 0
        }
        return try await body()
    }
}
