import Foundation
import WrModels
import WrNetwork

/// Asks the backend which actions the writer wants, and which folders a document belongs to.
/// The backend holds the TypeSafe key. Abstracted so the editor can be tested without the network.
public protocol WritingAgentServing: AnyObject {
    func suggestions(_ request: WritingSuggestionsRequest) async throws -> WritingSuggestionsResponse
    func folderSuggestions(_ request: FolderSuggestionsRequest) async throws -> FolderSuggestionsResponse
    /// Which of the workspace tags a document should carry.
    func tagSuggestions(_ request: TagSuggestionsRequest) async throws -> TagSuggestionsResponse
    /// A new tag, name and explanation, made up by the cloud AI for a document.
    func createTag(_ request: TagCreateRequest) async throws -> TagCreateResponse
}

/// Draws a picture for a paragraph. Only the cloud draws: the on-device models only write text.
public protocol AiImageGenerating: AnyObject {
    /// The URL of a picture for `paragraph`, stored with the user's media. The title and the text
    /// of the whole document go along, so the picture fits the subject and tone of the text.
    func generateImage(paragraph: String, documentTitle: String, documentText: String) async throws -> String
}

/// The writing agent routes of the Writeopia backend (`/api/ai/writing-suggestions`,
/// `/api/ai/folder-suggestions` and `/api/ai/image`), like `KtorWritingAgentApi` and
/// `GenAiApi.generateImage` of the Kotlin app.
public final class WritingAgentAPI: WritingAgentServing, AiImageGenerating {
    private let client: APIClient

    public init(client: APIClient) {
        self.client = client
    }

    public func suggestions(_ request: WritingSuggestionsRequest) async throws -> WritingSuggestionsResponse {
        try await client.send(.post, "api/ai/writing-suggestions", body: request)
    }

    public func folderSuggestions(_ request: FolderSuggestionsRequest) async throws -> FolderSuggestionsResponse {
        try await client.send(.post, "api/ai/folder-suggestions", body: request)
    }

    public func tagSuggestions(_ request: TagSuggestionsRequest) async throws -> TagSuggestionsResponse {
        try await client.send(.post, "api/ai/tag-suggestions", body: request)
    }

    public func createTag(_ request: TagCreateRequest) async throws -> TagCreateResponse {
        try await client.send(.post, "api/ai/tag-create", body: request)
    }

    public func generateImage(paragraph: String, documentTitle: String, documentText: String) async throws -> String {
        let response: AiImageResponse = try await client.send(
            .post,
            "api/ai/image",
            body: AiImageRequest(paragraph: paragraph, documentTitle: documentTitle, documentText: documentText)
        )
        guard let url = response.imageUrl, !url.isEmpty else {
            throw AiStreamError(message: response.error ?? String(localized: "The AI drew no picture"))
        }
        return url
    }
}
