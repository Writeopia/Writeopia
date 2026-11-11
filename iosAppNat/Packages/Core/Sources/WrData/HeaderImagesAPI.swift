import Foundation
import WrModels
import WrNetwork

/// The header pictures of a workspace on the Writeopia backend (`/api/ai/header-image` and
/// `/api/ai/header-images/{workspaceId}`), like `GenAiApi` of the Kotlin app. The backend draws
/// them with the cloud AI, charged to the user, and serves them from the CDN.
public final class HeaderImagesAPI {
    private let client: APIClient
    private let workspaceId: String

    public init(client: APIClient, workspaceId: String) {
        self.client = client
        self.workspaceId = workspaceId
    }

    /// Draws a header for a document. The result is the URL of a candidate, not yet approved.
    public func generateHeaderImage(documentTitle: String, documentText: String) async throws -> String {
        let response: HeaderImageGenerateResponse = try await client.send(
            .post,
            "api/ai/header-image",
            body: HeaderImageGenerateRequest(
                workspaceId: workspaceId,
                documentTitle: documentTitle,
                documentText: documentText
            )
        )
        guard let url = response.imageUrl, !url.isEmpty else {
            throw AiStreamError(message: response.error ?? String(localized: "The AI drew no picture"))
        }
        return url
    }

    /// The URLs of the approved pictures of the workspace, the newest first.
    public func headerImages() async throws -> [String] {
        let response: HeaderImagesResponse = try await client.get(path)
        return response.images.map(\.url)
    }

    /// The approved pictures of the workspace with their ids, the newest first, to manage them.
    public func pictures() async throws -> [HeaderImage] {
        let response: HeaderImagesResponse = try await client.get(path)
        return response.images
    }

    /// Takes a picture out of the pictures of the workspace. The documents that already use it
    /// as their header keep it, since it stays on the CDN. A picture already gone (deleted from
    /// another device) counts as deleted.
    public func deleteHeaderImage(id: String) async throws {
        do {
            try await client.perform(.delete, "\(path)/\(id)")
        } catch APIError.notFound {
            return
        }
    }

    /// Keeps an approved picture with the pictures of the workspace.
    public func saveHeaderImage(url: String) async throws {
        try await client.perform(.post, path, body: HeaderImageSaveRequest(imageUrl: url))
    }

    private var path: String { "api/ai/header-images/\(workspaceId)" }
}
