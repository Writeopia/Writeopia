import Foundation
import Testing
@testable import WrData
import WrModels
import WrNetwork
import WrStorage

/// Answers the link card description and the step sync like the backend does.
private final class DescribingBackend: HTTPTransport {
    var description = "A text editor for everyone."
    var describeStatus = 200
    private(set) var describeBody: [String: Any] = [:]
    private(set) var syncBodies: [[String: Any]] = []

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        let path = request.url!.path()
        let body = request.httpBody.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] } ?? [:]
        var status = 200
        var response = Data()

        switch path {
        case "/api/docs/workspace/w/document/d/link-card/describe":
            describeBody = body
            status = describeStatus
            let stepJson = body["storyStep"] as? [String: Any] ?? [:]
            var described = stepJson
            described["text"] = description
            described["lastUpdatedAt"] = body["requestTimestamp"]
            response = try JSONSerialization.data(withJSONObject: ["storyStep": described])
        case "/api/docs/workspace/w/document/d/steps/sync":
            syncBodies.append(body)
            var described = describeBody["storyStep"] as? [String: Any] ?? [:]
            described["text"] = description
            response = try JSONSerialization.data(withJSONObject: [
                "serverTimestamp": 1_000,
                "updatedSteps": [described],
                "deletedIds": [],
            ])
        default:
            status = 404
        }
        return (response, HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!)
    }
}

private func makeRepository(_ backend: DescribingBackend) -> SyncedDocumentsRepository {
    let client = APIClient(transport: backend, tokenStore: InMemoryTokenStore(accessToken: "a"), baseURL: URL(string: "https://x.io")!)
    let local = LocalDocumentsRepository(
        directory: FileManager.default.temporaryDirectory.appending(path: "links \(UUID().uuidString)", directoryHint: .isDirectory),
        workspaceId: "w",
        seedsWelcome: false
    )
    return SyncedDocumentsRepository(
        local: local,
        remote: RemoteDocumentsRepository(client: client, workspaceId: "w"),
        api: SyncAPI(client: client, workspaceId: "w")
    )
}

@Suite struct LinkCardDescriptionTests {
    @Test func describesTheCardThroughTheBackendAndReadsItBackFromTheSync() async throws {
        let backend = DescribingBackend()
        let repository = makeRepository(backend)
        let card = StoryStep(id: "card", type: .linkCard, url: "https://writeopia.io", position: Rank.at(1))

        let described = try await repository.describeLinkCard(documentId: "d", step: card)

        #expect(described.id == "card")
        #expect(described.text == "A text editor for everyone.")
        #expect(described.url == "https://writeopia.io")

        // The backend got the card, its position and the time of the request.
        let sentStep = try #require(backend.describeBody["storyStep"] as? [String: Any])
        #expect(sentStep["url"] as? String == "https://writeopia.io")
        #expect(backend.describeBody["position"] as? String == Rank.at(1))
        let requestTimestamp = try #require(backend.describeBody["requestTimestamp"] as? Int64)

        // Then the document was synced from just before the request, with nothing to send.
        let sync = try #require(backend.syncBodies.first)
        #expect(sync["lastSyncTimestamp"] as? Int64 == requestTimestamp - 1)
        #expect((sync["changes"] as? [Any])?.isEmpty == true)
    }

    @Test func aRefusedDescriptionThrows() async {
        let backend = DescribingBackend()
        backend.describeStatus = 502
        let repository = makeRepository(backend)
        let card = StoryStep(id: "card", type: .linkCard, url: "https://writeopia.io", position: Rank.at(1))

        await #expect(throws: Error.self) {
            try await repository.describeLinkCard(documentId: "d", step: card)
        }
        #expect(backend.syncBodies.isEmpty)
    }
}
