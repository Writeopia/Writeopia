import Foundation
import WrNetwork

/// Uploads images so documents in the open space can show them on every device.
public protocol ImageUploading: AnyObject {
    /// Uploads the image and returns its public URL.
    func uploadImage(_ data: Data, fileName: String, mimeType: String) async throws -> String
}

/// The media service, like `MediaApi` of the Compose app.
public final class MediaAPI: ImageUploading {
    private let client: APIClient

    public init(client: APIClient) {
        self.client = client
    }

    private struct UploadResponse: Decodable {
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
}
