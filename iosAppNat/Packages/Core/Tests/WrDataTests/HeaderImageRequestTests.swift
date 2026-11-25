import Foundation
import Testing
import WrModels

@Suite struct HeaderImageRequestTests {
    private func keys(_ request: HeaderImageGenerateRequest) throws -> Set<String> {
        let data = try JSONEncoder().encode(request)
        return Set((try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]).keys)
    }

    @Test func emptyInstructionsAreLeftOutSoOlderBackendsAcceptTheRequest() throws {
        #expect(try keys(HeaderImageGenerateRequest(workspaceId: "w", documentTitle: "t", documentText: "x"))
            == ["workspaceId", "documentTitle", "documentText"])
    }

    @Test func instructionsAreSentWhenTheUserWroteSome() throws {
        #expect(try keys(HeaderImageGenerateRequest(workspaceId: "w", instructions: "a red lighthouse")).contains("instructions"))
    }
}
