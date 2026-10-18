import Observation

/// Tells whether a document is being opened in the editor: read from the device, then fetched
/// from the backend and merged. The side menu shows a spinner by its folders while it is, so
/// the person knows the content they see may still change. Counterpart of
/// `DocumentLoadActivity` of the Compose app.
///
/// Loads may overlap (an editor opening while another one still merges), so the running ones
/// are counted: `isLoading` only goes back to false once the last one finishes.
@Observable
public final class DocumentLoadActivity {
    public private(set) var isLoading = false
    @ObservationIgnored private var running = 0

    public init() {}

    /// Runs `body` as a document load: `isLoading` holds true until it returns or throws.
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
