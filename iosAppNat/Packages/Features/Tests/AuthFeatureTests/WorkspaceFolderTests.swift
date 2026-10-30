import Foundation
import Testing
import WrData
import WrModels
import WrNetwork
import WrSession
import WrStorage

/// The folder each workspace is saved to and synced with, kept by `AppSession` per workspace.
@Suite struct WorkspaceFolderTests {
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

    private func makeFolder() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appending(path: "wr folder \(UUID().uuidString)", directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    @Test func theFolderIsKeptPerWorkspaceAcrossLaunches() throws {
        let privateFolder = try makeFolder()
        let teamFolder = try makeFolder()

        let session = makeSession()
        session.chooseOfflineSpace()
        #expect(session.workspaceFolder == nil)
        #expect(session.workspaceFolderSync != nil)

        try session.setWorkspaceFolder(privateFolder)
        #expect(session.workspaceFolder?.standardizedFileURL == privateFolder.standardizedFileURL)

        // Another workspace has its own folder, and none until picked.
        let team = Workspace(id: "w1", userId: "u1", name: "Team", role: "ADMIN")
        session.select(workspace: team)
        #expect(session.workspaceFolder == nil)
        try session.setWorkspaceFolder(teamFolder)
        #expect(session.workspaceFolder?.standardizedFileURL == teamFolder.standardizedFileURL)

        // Back to the private space, the first folder comes back.
        session.select(workspace: .local)
        #expect(session.workspaceFolder?.standardizedFileURL == privateFolder.standardizedFileURL)

        // The next launch opens the same folder.
        let relaunched = makeSession()
        #expect(relaunched.workspaceFolder?.standardizedFileURL == privateFolder.standardizedFileURL)

        try relaunched.setWorkspaceFolder(nil)
        #expect(relaunched.workspaceFolder == nil)
        #expect(makeSession().workspaceFolder == nil)
    }

    @Test func aFolderThatIsGoneIsForgotten() throws {
        let folder = try makeFolder()
        let session = makeSession()
        session.chooseOfflineSpace()
        try session.setWorkspaceFolder(folder)
        try FileManager.default.removeItem(at: folder)

        #expect(makeSession().workspaceFolder == nil)
    }
}
