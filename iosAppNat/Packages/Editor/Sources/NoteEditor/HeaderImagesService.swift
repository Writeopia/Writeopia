import Foundation
import WrData
import WriteopiaUI

/// The header pictures of the backend, for the header menu of `WriteopiaUI`, which doesn't know
/// the network.
final class HeaderImagesService: HeaderImageServing {
    private let api: HeaderImagesAPI

    init(api: HeaderImagesAPI) {
        self.api = api
    }

    func generateHeaderImage(documentTitle: String, documentText: String) async throws -> String {
        try await api.generateHeaderImage(documentTitle: documentTitle, documentText: documentText)
    }

    func headerImages() async throws -> [String] {
        try await api.headerImages()
    }

    func saveHeaderImage(url: String) async throws {
        try await api.saveHeaderImage(url: url)
    }
}
