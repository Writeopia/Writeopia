import Foundation
import Observation
import WrData
import WrModels
import WrNetwork
import WrStorage

/// Single source of truth for where the user is in the app (space choice, auth, main app) and
/// for the services shared by the feature modules.
@Observable
public final class AppSession {
    public enum Phase: Equatable {
        /// First launch or after "Switch space": pick the private or the open space.
        case spaceChoice
        /// Open space without a valid session.
        case signedOut
        /// Registered but the email was not confirmed yet.
        case emailConfirmation(email: String)
        /// Signed in, but no workspace (team) was selected.
        case chooseWorkspace
        case ready
    }

    public private(set) var phase: Phase
    public private(set) var spaceType: SpaceType?
    public private(set) var user: User?
    public private(set) var workspace: Workspace?
    public var colorTheme: ColorTheme {
        didSet { preferences.set(colorTheme.rawValue, for: .colorTheme) }
    }
    /// Who runs the AI commands. Apple Intelligence unless the user picks the cloud AI.
    public var aiProvider: AiProvider {
        didSet { preferences.set(aiProvider.rawValue, for: .aiProvider) }
    }

    public let client: APIClient
    public let authAPI: AuthAPI
    public let workspacesAPI: WorkspacesAPI
    public let aiAPI: AiAPI
    public let appleIntelligence: AppleIntelligenceAi
    public let preferences: Preferences
    private let tokenStore: TokenStore
    @ObservationIgnored private let isAppleIntelligenceAvailable: () -> Bool
    private let localDocuments: LocalDocumentsRepository
    @ObservationIgnored private var syncedDocuments: SyncedDocumentsRepository?

    public init(
        tokenStore: TokenStore = KeychainTokenStore(),
        preferences: Preferences = Preferences(),
        transport: HTTPTransport = URLSession.shared,
        localDocuments: LocalDocumentsRepository = LocalDocumentsRepository(),
        isAppleIntelligenceAvailable: @escaping () -> Bool = { AppleIntelligenceAi.isAvailable }
    ) {
        self.tokenStore = tokenStore
        self.isAppleIntelligenceAvailable = isAppleIntelligenceAvailable
        self.preferences = preferences
        self.localDocuments = localDocuments

        let client = APIClient(transport: transport, tokenStore: tokenStore)
        self.client = client
        authAPI = AuthAPI(client: client, tokenStore: tokenStore)
        workspacesAPI = WorkspacesAPI(client: client)
        aiAPI = AiAPI(client: client)
        appleIntelligence = AppleIntelligenceAi()

        spaceType = preferences.string(.spaceType).flatMap(SpaceType.init(rawValue:))
        user = preferences.codable(User.self, .currentUser)
        workspace = preferences.codable(Workspace.self, .selectedWorkspace)
        colorTheme = preferences.string(.colorTheme).flatMap(ColorTheme.init(rawValue:)) ?? .system
        aiProvider = preferences.string(.aiProvider).flatMap(AiProvider.init(rawValue:)) ?? .appleIntelligence
        phase = .spaceChoice
        phase = resolvePhase()

        client.onSessionExpired = { [weak self] in
            self?.sessionExpired()
        }
    }

    public var isOnline: Bool { spaceType == .online && tokenStore.accessToken != nil }

    /// Documents of the current workspace. The private space lives on the device; the open space
    /// is kept in a local cache per workspace and synced with the backend.
    public var documents: DocumentsRepository {
        guard spaceType == .online, let workspace, workspace.id != Workspace.localId else { return localDocuments }

        if let syncedDocuments, syncedDocuments.workspaceId == workspace.id {
            return syncedDocuments
        }
        let repository = SyncedDocumentsRepository(
            local: .cache(forWorkspace: workspace.id),
            remote: RemoteDocumentsRepository(client: client, workspaceId: workspace.id),
            api: SyncAPI(client: client, workspaceId: workspace.id)
        )
        syncedDocuments = repository
        return repository
    }

    /// Runs the AI commands of the editor and of the documents list; nil when no AI can answer.
    /// Apple Intelligence runs on the device, so it also works in the private space and offline.
    /// The cloud AI is used when picked in the open space, or when Apple Intelligence isn't
    /// available on this device.
    public var aiClient: AiStreaming? {
        let appleIntelligenceReady = isAppleIntelligenceAvailable()
        switch aiProvider {
        case .appleIntelligence:
            if appleIntelligenceReady { return appleIntelligence }
            return isOnline ? aiAPI : nil
        case .cloud:
            if isOnline { return aiAPI }
            return appleIntelligenceReady ? appleIntelligence : nil
        }
    }

    /// Image uploads; nil outside the open space, where images stay on the device.
    public var imageUploader: ImageUploading? {
        isOnline ? MediaAPI(client: client) : nil
    }

    /// Publishing for documents of the current workspace; nil outside the open space.
    public var publishing: DocumentPublishing? {
        guard isOnline, let workspace, workspace.id != Workspace.localId else { return nil }
        return PublishingAPI(client: client, workspaceId: workspace.id)
    }

    // MARK: - Space

    public func chooseOfflineSpace() {
        setSpace(.offline)
        workspace = .local
        preferences.setCodable(Workspace.local, for: .selectedWorkspace)
        phase = .ready
    }

    public func chooseOnlineSpace() {
        setSpace(.online)
        phase = resolvePhase()
    }

    /// Back to the "Choose your space" screen. Signed in users stay signed in.
    public func switchSpace() {
        setSpace(nil)
        phase = .spaceChoice
    }

    /// From the private space: go to the login screen of the open space.
    public func signIn() {
        setSpace(.online)
        workspace = nil
        preferences.remove(.selectedWorkspace)
        phase = resolvePhase()
    }

    // MARK: - Auth

    public func loggedIn(_ user: User) {
        setUser(user)
        preferences.remove(.pendingEmail)
        setSpace(.online)
        phase = .chooseWorkspace
    }

    public func needsEmailConfirmation(email: String) {
        preferences.set(email, for: .pendingEmail)
        phase = .emailConfirmation(email: email)
    }

    /// Leaves the email confirmation screen without confirming.
    public func cancelEmailConfirmation() {
        preferences.remove(.pendingEmail)
        phase = .signedOut
    }

    public func select(workspace: Workspace) {
        self.workspace = workspace
        preferences.setCodable(workspace, for: .selectedWorkspace)
        phase = .ready
    }

    /// Opens the workspace picker again, from Settings.
    public func changeWorkspace() {
        phase = .chooseWorkspace
    }

    public func updateUser(_ user: User) {
        setUser(user)
    }

    /// Signing out goes back to "Choose your space", like after the first launch.
    public func logout() async {
        await authAPI.logout()
        clearOnlineSession()
        switchSpace()
    }

    public func deleteAccount() async throws {
        try await authAPI.deleteAccount()
        clearOnlineSession()
        switchSpace()
    }

    // MARK: - Private

    private func sessionExpired() {
        guard spaceType == .online else { return }
        tokenStore.clear()
        clearOnlineSession()
    }

    private func clearOnlineSession() {
        setUser(nil)
        workspace = nil
        syncedDocuments = nil
        preferences.remove(.selectedWorkspace)
        phase = spaceType == .online ? .signedOut : resolvePhase()
    }

    private func setSpace(_ space: SpaceType?) {
        spaceType = space
        preferences.set(space?.rawValue, for: .spaceType)
    }

    private func setUser(_ user: User?) {
        self.user = user
        preferences.setCodable(user, for: .currentUser)
    }

    private func resolvePhase() -> Phase {
        switch spaceType {
        case nil:
            return .spaceChoice
        case .offline:
            if workspace == nil {
                workspace = .local
            }
            return .ready
        case .online:
            if let pendingEmail = preferences.string(.pendingEmail) {
                return .emailConfirmation(email: pendingEmail)
            }
            guard tokenStore.accessToken != nil else { return .signedOut }
            guard let workspace, workspace.id != Workspace.localId else { return .chooseWorkspace }
            return .ready
        }
    }
}
