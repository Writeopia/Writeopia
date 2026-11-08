import Foundation
import WrModels
import WrNetwork

/// Uploads images so documents in the open space can show them on every device.
public protocol ImageUploading: AnyObject {
    /// Uploads the image and returns its public URL.
    func uploadImage(_ data: Data, fileName: String, mimeType: String) async throws -> String

    /// Deletes an image this uploader stored, when the user removes it from the document, so
    /// the backend gives its space back.
    func deleteImage(url: String) async throws
}

public extension ImageUploading {
    func deleteImage(url: String) async throws {}
}

/// The media service, like `MediaApi` of the Compose app. Every picture counts in the user's
/// storage: a full one refuses the upload with `APIError.storageFull`.
public final class MediaAPI: ImageUploading {
    private let client: APIClient

    public init(client: APIClient) {
        self.client = client
    }

    private struct UploadResponse: Decodable {
        let imageUrl: String
    }

    private struct DeleteBody: Encodable {
        let imageUrl: String
    }

    public func uploadImage(_ data: Data, fileName: String, mimeType: String) async throws -> String {
        let boundary = "WriteopiaBoundary-\(UUID().uuidString)"
        let body = APIClient.multipartBody(field: "image", fileName: fileName, mimeType: mimeType, data: data, boundary: boundary)
        let response = try await client.upload("api/media/upload", body: body, contentType: "multipart/form-data; boundary=\(boundary)")
        do {
            return try JSONDecoder().decode(UploadResponse.self, from: response).imageUrl
        } catch {
            throw APIError.decoding
        }
    }

    /// How much media the user keeps, and the quota.
    public func usage() async throws -> StorageUsage {
        try await client.get("api/media/usage")
    }

    /// Deletes one of the user's pictures on the backend; its bytes leave the storage.
    public func deleteImage(url: String) async throws {
        try await client.perform(.post, "api/media/delete", body: DeleteBody(imageUrl: url))
    }
}
