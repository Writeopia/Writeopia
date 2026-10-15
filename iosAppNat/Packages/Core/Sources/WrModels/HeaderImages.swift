import Foundation

/// What the backend needs to draw the header of a document, `HeaderImageGenerateRequest` of the
/// Kotlin app.
public struct HeaderImageGenerateRequest: Encodable, Equatable, Sendable {
    public let workspaceId: String
    public let documentTitle: String
    public let documentText: String

    public init(workspaceId: String, documentTitle: String = "", documentText: String = "") {
        self.workspaceId = workspaceId
        self.documentTitle = documentTitle
        self.documentText = documentText
    }
}

/// The drawn header, already on the CDN, or why there is none. Only a candidate until approved.
public struct HeaderImageGenerateResponse: Decodable, Equatable, Sendable {
    public let imageUrl: String?
    public let error: String?

    public init(imageUrl: String? = nil, error: String? = nil) {
        self.imageUrl = imageUrl
        self.error = error
    }
}

/// A header picture of a workspace, that any document of the workspace can use.
public struct HeaderImage: Codable, Equatable, Sendable {
    public let id: String
    public let url: String
    public let createdAt: Int64

    public init(id: String, url: String, createdAt: Int64) {
        self.id = id
        self.url = url
        self.createdAt = createdAt
    }
}

public struct HeaderImagesResponse: Decodable, Equatable, Sendable {
    public let images: [HeaderImage]

    public init(images: [HeaderImage] = []) {
        self.images = images
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        images = try container.decodeIfPresent([HeaderImage].self, forKey: .images) ?? []
    }

    private enum CodingKeys: String, CodingKey {
        case images
    }
}

/// A generated header the user approved, to keep with the pictures of the workspace.
public struct HeaderImageSaveRequest: Encodable, Equatable, Sendable {
    public let imageUrl: String

    public init(imageUrl: String) {
        self.imageUrl = imageUrl
    }
}
