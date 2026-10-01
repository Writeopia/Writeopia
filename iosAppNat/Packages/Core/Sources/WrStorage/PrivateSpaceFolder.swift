import Foundation

/// A folder the user picked for the private space, kept across launches as a security-scoped
/// bookmark so the sandboxed Mac app can open it again.
public enum PrivateSpaceFolder {
    public struct UnreadableFolder: Error {
        public init() {}
    }

    /// A bookmark of `url`. The URL must come from the user (open panel or file importer).
    public static func bookmark(for url: URL) throws -> Data {
        #if os(macOS)
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }
        return try url.bookmarkData(options: .withSecurityScope, includingResourceValuesForKeys: nil, relativeTo: nil)
        #else
        try url.bookmarkData(options: .minimalBookmark, includingResourceValuesForKeys: nil, relativeTo: nil)
        #endif
    }

    /// Resolves a bookmark and starts accessing the folder. Access is kept for the life of the
    /// process because the documents database stays open. `isStale` asks for a new bookmark.
    public static func resolve(_ bookmark: Data) -> (url: URL, isStale: Bool)? {
        var isStale = false
        #if os(macOS)
        guard let url = try? URL(resolvingBookmarkData: bookmark, options: .withSecurityScope, relativeTo: nil, bookmarkDataIsStale: &isStale) else {
            return nil
        }
        guard url.startAccessingSecurityScopedResource() else { return nil }
        #else
        guard let url = try? URL(resolvingBookmarkData: bookmark, relativeTo: nil, bookmarkDataIsStale: &isStale) else {
            return nil
        }
        #endif
        return (url, isStale)
    }
}
