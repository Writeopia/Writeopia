import Foundation
import WrNetwork

/// Publishing documents as public web pages. Abstracted so the editor can be tested offline.
public protocol DocumentPublishing: AnyObject {
    func isPublished(documentId: String) async throws -> Bool
    func publish(documentId: String) async throws
    func unpublish(documentId: String) async throws
}

public final class PublishingAPI: DocumentPublishing {
    /// Public address of a published document, the same one the Compose app shares.
    public static func siteURL(documentId: String) -> URL {
        URL(string: "https://app.writeopia.io/site/\(documentId)")!
    }

    private let client: APIClient
    private let workspaceId: String

    public init(client: APIClient, workspaceId: String) {
        self.client = client
        self.workspaceId = workspaceId
    }

    private func path(_ documentId: String, _ action: String) -> String {
        "api/docs/workspace/\(workspaceId)/document/\(documentId)/\(action)"
    }

    public func isPublished(documentId: String) async throws -> Bool {
        let response: [String: Bool] = try await client.get(path(documentId, "published"))
        return response["published"] ?? false
    }

    public func publish(documentId: String) async throws {
        try await client.perform(.post, path(documentId, "publish"))
    }

    public func unpublish(documentId: String) async throws {
        try await client.perform(.post, path(documentId, "unpublish"))
    }
}
