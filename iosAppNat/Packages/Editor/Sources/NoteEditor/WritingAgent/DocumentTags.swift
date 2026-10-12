import Foundation
import WrData
import WrModels

/// What the agent needs to tag the open document: which tags it has, which it could have, and
/// how to add them. The editor provides this; the agent decides when to offer it.
public protocol DocumentTags: AnyObject {
    /// The ids of the tags the open document carries right now.
    func currentTagIds() -> [String]
    /// The workspace tags the document could carry, at most `limit`, by name.
    func candidates(limit: Int) async throws -> [TagCandidate]
    /// Adds the tags with `tagIds` to the open document.
    func addTags(_ tagIds: [String]) async throws
    /// Makes a tag for the document, named and explained by the cloud AI from its title and
    /// text, stores it in the catalogue and adds it to the document. Returns the new tag.
    func createTagWithAi(documentTitle: String, documentText: String) async throws -> DocumentTag
}

/// Tags the open document with the app's documents repository, which holds the catalogue. A new
/// tag is made up by the backend's cloud AI, stored, sent when possible, and added to the document.
public final class RepositoryDocumentTags: DocumentTags {
    private let workspaceId: () -> String
    private let tagIds: () -> [String]
    private let repository: DocumentsRepository
    private let api: WritingAgentServing
    /// Tells the editor the tags were added, so what it saves next carries them.
    private let onAdded: ([String]) -> Void

    public init(
        workspaceId: @escaping () -> String,
        tagIds: @escaping () -> [String],
        repository: DocumentsRepository,
        api: WritingAgentServing,
        onAdded: @escaping ([String]) -> Void
    ) {
        self.workspaceId = workspaceId
        self.tagIds = tagIds
        self.repository = repository
        self.api = api
        self.onAdded = onAdded
    }

    public func currentTagIds() -> [String] { tagIds() }

    public func candidates(limit: Int) async throws -> [TagCandidate] {
        let current = Set(tagIds())
        return try await repository.tags()
            .filter { !$0.name.isEmpty && !current.contains($0.id) }
            .prefix(limit)
            .map { TagCandidate(id: $0.id, name: $0.name, description: $0.description) }
    }

    public func addTags(_ tagIds: [String]) async throws {
        onAdded(tagIds)
    }

    public func createTagWithAi(documentTitle: String, documentText: String) async throws -> DocumentTag {
        let existing = try await repository.tags().map(\.name)
        let answer = try await api.createTag(
            TagCreateRequest(documentTitle: documentTitle, documentText: documentText, existingTagNames: existing)
        )
        guard answer.error == nil, !answer.name.isEmpty else { throw WritingAgentError.emptyAnswer }

        let tag = DocumentTag(workspaceId: workspaceId(), name: answer.name, description: answer.description)
        WritingActionExecutor.logger.info("created tag \"\(tag.name, privacy: .public)\" (\(tag.id, privacy: .public)) from the AI")
        try await repository.saveTag(tag)
        onAdded([tag.id])
        return tag
    }
}
