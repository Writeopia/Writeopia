import Foundation
import WrModels
import WrNetwork

/// The presentations of the open space: the backend asks the AI, parses the slides and keeps
/// them, so the app only shows what comes back. Every presentation the backend sends is also
/// saved in `store` (the app database), so the search finds it and it opens offline; when the
/// backend can't be reached, the presentations are read from there.
public final class PresentationsAPI: PresentationsRepository, PresentationGenerating {
    private let client: APIClient
    public let workspaceId: String
    private let store: PresentationsStore?

    public init(client: APIClient, workspaceId: String, store: PresentationsStore? = nil) {
        self.client = client
        self.workspaceId = workspaceId
        self.store = store
    }

    private func keep(_ presentations: [Presentation]) async {
        for presentation in presentations {
            try? await store?.savePresentation(presentation)
        }
    }

    private var base: String { "api/docs/workspace/\(workspaceId)" }

    private struct GenerateBody: Encodable {
        var model: String?
    }

    private struct GenerateResponse: Decodable {
        var presentation: Presentation?
        var error: String?
    }

    private struct ListResponse: Decodable {
        var presentations: [Presentation]
    }

    public func presentations(ofDocument documentId: String) async throws -> [Presentation] {
        do {
            let response: ListResponse = try await client.get("\(base)/document/\(documentId)/presentations")
            await keep(response.presentations)
            return response.presentations
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            if let saved = try? await store?.presentations(ofDocument: documentId), !saved.isEmpty {
                return saved
            }
            throw error
        }
    }

    public func presentation(id: String) async throws -> Presentation? {
        do {
            let presentation: Presentation = try await client.get("\(base)/presentation/\(id)")
            await keep([presentation])
            return presentation
        } catch APIError.notFound {
            return nil
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            if let saved = try? await store?.presentation(id: id) {
                return saved
            }
            throw error
        }
    }

    public func deletePresentation(id: String) async throws {
        try await client.perform(.delete, "\(base)/presentation/\(id)")
        try? await store?.deletePresentation(id: id)
    }

    public func generatePresentation(documentId: String) async throws -> Presentation {
        let response: GenerateResponse = try await client.send(
            .post,
            "\(base)/document/\(documentId)/presentations",
            body: GenerateBody()
        )
        if let presentation = response.presentation {
            await keep([presentation])
            return presentation
        }
        throw PresentationError(message: response.error ?? String(localized: "The AI didn't return any slide."))
    }
}
