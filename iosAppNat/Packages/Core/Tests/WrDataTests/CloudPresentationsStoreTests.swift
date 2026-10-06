import Foundation
import Testing
@testable import WrData
import WrModels
import WrNetwork
import WrStorage

/// The backend of the presentations: answers with `presentations`, or fails like when it can't
/// be reached.
private final class PresentationsBackend: HTTPTransport {
    var presentations: [Presentation] = []
    var offline = false

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        if offline { throw URLError(.notConnectedToInternet) }
        let path = request.url!.path()
        var status = 200
        var body = Data()

        if request.httpMethod == "DELETE" {
            body = Data()
        } else if path.hasSuffix("/presentations") && request.httpMethod == "POST" {
            let generated = Presentation(id: "generated", documentId: "d1", title: "Generated", createdAt: 9, slides: [Slide(title: "New")])
            body = try JSONEncoder().encode(["presentation": generated])
        } else if path.hasSuffix("/presentations") {
            body = try JSONEncoder().encode(["presentations": presentations])
        } else if let id = path.split(separator: "/").last, let found = presentations.first(where: { $0.id == id }) {
            body = try JSONEncoder().encode(found)
        } else {
            status = 404
        }
        return (body, HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!)
    }
}

@Suite struct CloudPresentationsStoreTests {
    let directory = FileManager.default.temporaryDirectory
        .appending(path: "wr tests \(UUID().uuidString)", directoryHint: .isDirectory)

    private func makeAPI(_ backend: PresentationsBackend, store: LocalDocumentsRepository) -> PresentationsAPI {
        let client = APIClient(transport: backend, tokenStore: InMemoryTokenStore(accessToken: "a"), baseURL: URL(string: "https://x.io")!)
        return PresentationsAPI(client: client, workspaceId: "w", store: store)
    }

    private func sky(_ id: String) -> Presentation {
        Presentation(id: id, documentId: "d1", title: "Sky \(id)", createdAt: 1, slides: [Slide(title: "Sky")])
    }

    @Test func whatTheCloudSendsIsKeptAndFoundByTheSearch() async throws {
        let backend = PresentationsBackend()
        backend.presentations = [sky("listed")]
        let store = LocalDocumentsRepository(directory: directory, seedsWelcome: false)
        let api = makeAPI(backend, store: store)

        _ = try await api.generatePresentation(documentId: "d1")
        _ = try await api.presentations(ofDocument: "d1")

        #expect(Set(try await store.searchPresentations(query: "").map(\.id)).isEmpty)
        #expect(Set(try await store.searchPresentations(query: "sky").map(\.id)) == ["listed"])
        #expect(try await store.presentation(id: "generated")?.slides.map(\.title) == ["New"])
    }

    @Test func offlineThePresentationsComeFromTheDevice() async throws {
        let backend = PresentationsBackend()
        backend.presentations = [sky("p1")]
        let store = LocalDocumentsRepository(directory: directory, seedsWelcome: false)
        let api = makeAPI(backend, store: store)
        _ = try await api.presentations(ofDocument: "d1")

        backend.offline = true

        #expect(try await api.presentations(ofDocument: "d1").map(\.id) == ["p1"])
        #expect(try await api.presentation(id: "p1")?.slides.map(\.title) == ["Sky"])
        await #expect(throws: (any Error).self) { try await api.presentation(id: "unknown") }
    }

    @Test func deletingRemovesTheCopyOnlyWhenTheCloudDeletedIt() async throws {
        let backend = PresentationsBackend()
        backend.presentations = [sky("p1"), sky("p2")]
        let store = LocalDocumentsRepository(directory: directory, seedsWelcome: false)
        let api = makeAPI(backend, store: store)
        _ = try await api.presentations(ofDocument: "d1")

        try await api.deletePresentation(id: "p1")
        backend.offline = true
        _ = try? await api.deletePresentation(id: "p2")

        #expect(try await store.presentation(id: "p1") == nil)
        #expect(try await store.presentation(id: "p2") != nil)
    }
}
