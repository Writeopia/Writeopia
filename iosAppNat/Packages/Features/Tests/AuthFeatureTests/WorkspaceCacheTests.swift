import Foundation
import Testing
import WrData
import WrModels
import WrNetwork
import WrSession
import WrStorage

/// The workspaces the picker lists are kept on the device, so they show before (or without) the
/// backend answering.
@Suite struct WorkspaceCacheTests {
    private let defaults = UserDefaults(suiteName: "tests.\(UUID().uuidString)")!

    private func makeSession() -> AppSession {
        AppSession(
            tokenStore: InMemoryTokenStore(),
            preferences: Preferences(defaults: defaults),
            localDocuments: LocalDocumentsRepository(
                directory: FileManager.default.temporaryDirectory.appending(path: "wr tests \(UUID().uuidString)", directoryHint: .isDirectory),
                seedsWelcome: false
            )
        )
    }

    @Test func nothingIsCachedBeforeTheFirstLoad() {
        #expect(makeSession().cachedWorkspaces().isEmpty)
    }

    @Test func theCachedWorkspacesComeBackWithRoleAndCount() {
        let session = makeSession()
        let team = Workspace(id: "w1", userId: "u1", name: "Team", role: "ADMIN", documentCount: 12)

        session.cacheWorkspaces([team])

        // A new session on the same preferences, like the next launch.
        #expect(makeSession().cachedWorkspaces() == [team])
    }

    @Test func aNewListReplacesTheCachedOne() {
        let session = makeSession()
        session.cacheWorkspaces([Workspace(id: "w1", userId: "u1", name: "Old", role: "USER")])
        session.cacheWorkspaces([Workspace(id: "w2", userId: "u1", name: "New", role: "USER")])

        #expect(session.cachedWorkspaces().map(\.id) == ["w2"])
    }
}
