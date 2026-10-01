import Foundation
import WrModels
import WrNetwork

/// The presentations of the open space: the backend asks the AI, parses the slides and keeps
/// them, so the app only shows what comes back.
public final class PresentationsAPI: PresentationsRepository, PresentationGenerating {
    private let client: APIClient
    public let workspaceId: String

    public init(client: APIClient, workspaceId: String) {
        self.client = client
        self.workspaceId = workspaceId
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
        let response: ListResponse = try await client.get("\(base)/document/\(documentId)/presentations")
        return response.presentations
    }

    public func presentation(id: String) async throws -> Presentation? {
        do {
            return try await client.get("\(base)/presentation/\(id)")
        } catch APIError.notFound {
            return nil
        }
    }

    public func deletePresentation(id: String) async throws {
        try await client.perform(.delete, "\(base)/presentation/\(id)")
    }

    public func generatePresentation(documentId: String) async throws -> Presentation {
        let response: GenerateResponse = try await client.send(
            .post,
            "\(base)/document/\(documentId)/presentations",
            body: GenerateBody()
        )
        if let presentation = response.presentation {
            return presentation
        }
        throw PresentationError(message: response.error ?? String(localized: "The AI didn't return any slide."))
    }
}
