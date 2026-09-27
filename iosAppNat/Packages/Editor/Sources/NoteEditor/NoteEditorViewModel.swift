import Foundation
import Observation
import Writeopia
import WriteopiaUI
import WrData
import WrModels
import WrNetwork

/// Loads a document into the editor. Counterpart of `NoteEditorKmpViewModel`, reduced to
/// loading: changes are kept in memory and not saved yet.
@Observable
public final class NoteEditorViewModel {
    public let writeopiaManager: WriteopiaStateManager
    public private(set) var isLoading = false
    public private(set) var errorMessage: String?
    public private(set) var hasLoaded = false

    private let documentId: String
    private let repository: DocumentsRepository

    public init(
        documentId: String,
        repository: DocumentsRepository,
        writeopiaManager: WriteopiaStateManager = WriteopiaStateManager()
    ) {
        self.documentId = documentId
        self.repository = repository
        self.writeopiaManager = writeopiaManager
    }

    /// Title as it is being typed.
    public var title: String { writeopiaManager.title }

    public func loadDocument() async {
        // Reloading would throw away the edits, which are only in memory.
        guard !hasLoaded else { return }

        isLoading = true
        defer { isLoading = false }

        do {
            let document = try await repository.document(id: documentId)
            writeopiaManager.loadDocument(document)
            hasLoaded = true
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.userMessage
        }
    }
}
