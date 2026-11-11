import Foundation
import Observation
import WrData
import WrModels
import WrNetwork

/// What the header pictures page needs of the backend: `HeaderImagesAPI`, or a fake in tests.
protocol HeaderImagesManaging {
    func pictures() async throws -> [HeaderImage]
    func deleteHeaderImage(id: String) async throws
}

extension HeaderImagesAPI: HeaderImagesManaging {}

/// The header pictures of the workspace, to delete them, like `WorkspaceHeaderImagesController`
/// of the Compose app. A deleted picture leaves the pictures the header menu offers; the
/// documents that use it keep it, since it stays on the CDN.
@Observable
final class HeaderImagesSettingsModel {
    private(set) var images: [HeaderImage] = []
    private(set) var isLoading = false
    private(set) var errorMessage: String?
    /// The pictures being deleted, drawn dimmed until the backend answers.
    private(set) var deleting: Set<String> = []
    var deleteError: String?

    /// Nil outside an online workspace: the pictures live on the backend only.
    private let source: HeaderImagesManaging?
    /// The pictures deleted here, which a listing that started before may still carry.
    private var deleted: Set<String> = []

    init(source: HeaderImagesManaging?) {
        self.source = source
    }

    var isAvailable: Bool { source != nil }

    func load() async {
        guard let source else { return }
        isLoading = true
        defer { isLoading = false }

        do {
            let listed = try await source.pictures()
            images = listed.filter { !deleted.contains($0.id) }
            errorMessage = nil
        } catch {
            errorMessage = error.userMessage
        }
    }

    func delete(_ image: HeaderImage) async {
        guard let source, !deleting.contains(image.id) else { return }
        deleting.insert(image.id)
        deleteError = nil
        defer { deleting.remove(image.id) }

        do {
            try await source.deleteHeaderImage(id: image.id)
            deleted.insert(image.id)
            images.removeAll { $0.id == image.id }
        } catch {
            deleteError = String(localized: "The header image could not be deleted.")
        }
    }
}

/// The tag catalogue of the workspace, to create, edit and delete tags, like
/// `WorkspaceTagsController` of the Compose app. Every change goes through `saveTag`, which
/// stores it and sends it right away in the open space (the folder sync sends it later when that
/// fails). A deleted tag is a tombstone, so the removal reaches the other devices.
@Observable
final class TagsSettingsModel {
    private(set) var tags: [DocumentTag] = []
    private(set) var isLoading = false
    var errorMessage: String?

    private let repository: DocumentsRepository
    private let workspaceId: String
    /// The last save: each one waits for the one before, so the backend, which keeps whichever
    /// copy of a tag arrives last, gets the changes in the order they were made.
    private var lastSave: Task<String?, Never>?

    init(repository: DocumentsRepository, workspaceId: String) {
        self.repository = repository
        self.workspaceId = workspaceId
    }

    func load() async {
        isLoading = true
        defer { isLoading = false }

        do {
            tags = Self.sorted(try await repository.tags().filter { !$0.deleted })
            errorMessage = nil
        } catch {
            errorMessage = error.userMessage
        }
    }

    func create(name: String, description: String) async {
        let name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }

        let tag = DocumentTag(
            workspaceId: workspaceId,
            name: name,
            description: description.trimmingCharacters(in: .whitespacesAndNewlines)
        )
        await save(tag)
    }

    func edit(_ tag: DocumentTag, name: String, description: String) async {
        let name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let description = description.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, name != tag.name || description != tag.description else { return }

        var edited = tag
        edited.name = name
        edited.description = description
        edited.lastUpdatedAt = Date.nowMillis
        await save(edited)
    }

    func delete(_ tag: DocumentTag) async {
        var tombstone = tag
        tombstone.deleted = true
        tombstone.lastUpdatedAt = Date.nowMillis
        await save(tombstone)
    }

    /// Whether another tag already has `name`, ignoring case and the spaces around it.
    static func isNameTaken(_ name: String, in tags: [DocumentTag], except id: String? = nil) -> Bool {
        let wanted = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !wanted.isEmpty else { return false }
        return tags.contains { tag in
            tag.id != id && tag.name.trimmingCharacters(in: .whitespacesAndNewlines)
                .caseInsensitiveCompare(wanted) == .orderedSame
        }
    }

    /// Shows the change right away, so the form's name check sees it while the save (which
    /// may wait for the backend) runs, then reloads what was stored. A save error outlives the
    /// reload.
    private func save(_ tag: DocumentTag) async {
        var shown = tags.filter { $0.id != tag.id }
        if !tag.deleted { shown.append(tag) }
        tags = Self.sorted(shown)

        let previous = lastSave
        let repository = repository
        let save = Task<String?, Never> {
            _ = await previous?.value
            do {
                try await repository.saveTag(tag)
                return nil
            } catch {
                return error.userMessage
            }
        }
        lastSave = save

        let saveError = await save.value
        await load()
        if let saveError { errorMessage = saveError }
    }

    private static func sorted(_ tags: [DocumentTag]) -> [DocumentTag] {
        tags.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }
}
