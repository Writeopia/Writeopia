import Foundation

/// How the folders and documents of a folder are shown. Mirrors `NotesArrangement` of the
/// Compose app, raw values included.
public enum DocumentsArrangement: String, CaseIterable, Sendable {
    case list
    case grid
    case staggeredGrid = "staggered_grid"
}

/// Order of the folders and documents of a folder. Mirrors `OrderBy` of the Compose app, raw
/// values included.
public enum DocumentsOrder: String, CaseIterable, Sendable {
    case updated = "last_updated_at"
    case created = "created_at"
    case name = "title"
}
