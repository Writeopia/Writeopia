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
        /// First-run setup of the private space (the Mac app): local AI, then the folder.
        case offlineSetup(OfflineSetupStep)
        case ready
    }

    /// The setup screens shown after choosing the private space, like the desktop app in Compose.
    public enum OfflineSetupStep: Equatable, Sendable {
        case localAi
        case localFolder
    }

    public private(set) var phase: Phase
    public private(set) var spaceType: SpaceType?
    public private(set) var user: User?
    public private(set) var workspace: Workspace?
    /// Folder the user picked for the private space; nil for the default location.
    public private(set) var privateSpaceFolder: URL?
    /// Bumped when the private space moves, so screens holding a repository reload.
    public private(set) var documentsVersion = 0
    public var colorTheme: ColorTheme {
        didSet { preferences.set(colorTheme.rawValue, for: .colorTheme) }
    }
    /// Who runs the AI commands. Apple Intelligence unless the user picks the cloud AI.
    public var aiProvider: AiProvider {
        didSet { preferences.set(aiProvider.rawValue, for: .aiProvider) }
    }

    /// Where Ollama or llmman answers. `LOCAL_AI_URL` in the environment overrides it.
    public var localAiURL: URL {
        didSet {
            preferences.set(localAiURL.absoluteString, for: .localAiURL)
            rebuildLocalAi()
        }
    }
    /// The model of the local AI; nil until one is picked.
    public var localAiModel: String? {
        didSet {
            preferences.set(localAiModel, for: .localAiModel)
            rebuildLocalAi()
        }
    }
    public private(set) var ollamaAPI: OllamaAPI
    /// The local AI when a model is picked.
    public private(set) var ollamaAi: OllamaAi?
    public let localAiConfig: LocalAiConfigController

    public let client: APIClient
    public let authAPI: AuthAPI
    public let workspacesAPI: WorkspacesAPI
    public let aiAPI: AiAPI
    public let appleIntelligence: AppleIntelligenceAi
    public let preferences: Preferences
    private let tokenStore: TokenStore
    @ObservationIgnored private let isAppleIntelligenceAvailable: () -> Bool
    private var localDocuments: LocalDocumentsRepository
    @ObservationIgnored private let offlineSetupSteps: [OfflineSetupStep]
    @ObservationIgnored private var syncedDocuments: SyncedDocumentsRepository?

    public init(
        tokenStore: TokenStore = KeychainTokenStore(),
        preferences: Preferences = Preferences(),
        transport: HTTPTransport = URLSession.shared,
        localDocuments: LocalDocumentsRepository? = nil,
        offlineSetupSteps: [OfflineSetupStep] = [],
        isAppleIntelligenceAvailable: @escaping () -> Bool = { AppleIntelligenceAi.isAvailable }
    ) {
        self.tokenStore = tokenStore
        self.isAppleIntelligenceAvailable = isAppleIntelligenceAvailable
        self.preferences = preferences
        self.offlineSetupSteps = offlineSetupSteps
        if let localDocuments {
            self.localDocuments = localDocuments
        } else {
            let (repository, folder) = Self.openPrivateSpace(preferences: preferences)
            self.localDocuments = repository
            privateSpaceFolder = folder
        }

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

        let localAiURL = ProcessInfo.processInfo.environment["LOCAL_AI_URL"].flatMap(URL.init(string:))
            ?? preferences.string(.localAiURL).flatMap(URL.init(string:))
            ?? OllamaAPI.defaultURL
        let localAiModel = preferences.string(.localAiModel)
        self.localAiURL = localAiURL
        self.localAiModel = localAiModel
        ollamaAPI = OllamaAPI(baseURL: localAiURL)
        ollamaAi = localAiModel.map { OllamaAi(api: OllamaAPI(baseURL: localAiURL), model: $0) }
        let autoConfigAPI = LocalAiAutoConfigAPI(client: client)
        localAiConfig = LocalAiConfigController(url: localAiURL, selectedModel: localAiModel) {
            await autoConfigAPI.config()
        }

        phase = .spaceChoice
        phase = resolvePhase()

        client.onSessionExpired = { [weak self] in
            self?.sessionExpired()
        }
        localAiConfig.onChange = { [weak self] url, model in
            guard let self else { return }
            if localAiURL != url { self.localAiURL = url }
            if localAiModel != model { self.localAiModel = model }
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
    public var aiClient: AiStreaming? { resolvedAi?.client }

    /// The AI that answers right now, with the provider it belongs to: the one picked, or the
    /// one it falls back to.
    public var resolvedAi: (provider: AiProvider, client: AiStreaming)? {
        let appleIntelligenceReady = isAppleIntelligenceAvailable()
        // The cloud AI needs a session and a workspace of the open space.
        let cloudReady = isOnline && workspace.map { $0.id != Workspace.localId } == true
        switch aiProvider {
        case .appleIntelligence:
            if appleIntelligenceReady { return (.appleIntelligence, appleIntelligence) }
            return cloudReady ? (.cloud, aiAPI) : nil
        case .cloud:
            if cloudReady { return (.cloud, aiAPI) }
            return appleIntelligenceReady ? (.appleIntelligence, appleIntelligence) : nil
        case .ollama:
            if let ollamaAi { return (.ollama, ollamaAi) }
            if appleIntelligenceReady { return (.appleIntelligence, appleIntelligence) }
            return cloudReady ? (.cloud, aiAPI) : nil
        }
    }

    /// Where the presentations of the documents come from: the backend with the cloud AI, the
    /// device with Ollama. Nil with Apple Intelligence, too small for a whole document.
    public var presentationsSource: PresentationsSource? {
        switch resolvedAi?.provider {
        case .cloud:
            guard let workspace, workspace.id != Workspace.localId else { return nil }
            return .cloud(PresentationsAPI(client: client, workspaceId: workspace.id))
        case .ollama:
            return .local
        case .appleIntelligence, nil:
            return nil
        }
    }

    /// Reads the presentations `presentationsSource` makes, e.g. for the presentation window.
    public var presentationsRepository: PresentationsRepository? {
        switch presentationsSource {
        case .cloud(let api): api
        case .local: documents as? PresentationsRepository
        case nil: nil
        }
    }

    public var supportsPresentations: Bool { presentationsSource != nil }

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
        // Saved before the setup screens, like `useOffline` of the Compose app: quitting in the
        // middle of the setup lands in the app next time.
        phase = offlineSetupSteps.first.map(Phase.offlineSetup) ?? .ready
    }

    /// Continues to the next setup screen of the private space, or to the app.
    public func advanceOfflineSetup() {
        guard case .offlineSetup(let step) = phase, let index = offlineSetupSteps.firstIndex(of: step) else { return }
        let next = offlineSetupSteps.index(after: index)
        phase = next < offlineSetupSteps.endIndex ? .offlineSetup(offlineSetupSteps[next]) : .ready
    }

    /// Back to the previous setup screen, or to "Choose your space" from the first one.
    public func retreatOfflineSetup() {
        guard case .offlineSetup(let step) = phase, let index = offlineSetupSteps.firstIndex(of: step) else { return }
        if index == offlineSetupSteps.startIndex {
            switchSpace()
        } else {
            phase = .offlineSetup(offlineSetupSteps[offlineSetupSteps.index(before: index)])
        }
    }

    // MARK: - Private space folder

    /// Keeps the private space in `url` (a folder the user picked), or back in the default
    /// location with nil. Documents already in the previous location are not moved.
    public func setPrivateSpaceFolder(_ url: URL?) throws {
        guard let url else {
            preferences.remove(.privateSpaceFolderBookmark)
            localDocuments = LocalDocumentsRepository()
            privateSpaceFolder = nil
            documentsVersion += 1
            return
        }

        let bookmark = try PrivateSpaceFolder.bookmark(for: url)
        guard let resolved = PrivateSpaceFolder.resolve(bookmark) else { throw PrivateSpaceFolder.UnreadableFolder() }
        let repository = try LocalDocumentsRepository.inUserFolder(resolved.url)
        preferences.set(bookmark, for: .privateSpaceFolderBookmark)
        localDocuments = repository
        privateSpaceFolder = resolved.url
        documentsVersion += 1
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

    private func rebuildLocalAi() {
        ollamaAPI = OllamaAPI(baseURL: localAiURL)
        ollamaAi = localAiModel.flatMap { $0.isEmpty ? nil : OllamaAi(api: ollamaAPI, model: $0) }
    }

    private func setSpace(_ space: SpaceType?) {
        spaceType = space
        preferences.set(space?.rawValue, for: .spaceType)
    }

    private func setUser(_ user: User?) {
        self.user = user
        preferences.setCodable(user, for: .currentUser)
    }

    /// The private space from the folder saved before, or the default one when there is none
    /// or it can't be opened anymore (the bookmark is then forgotten).
    private static func openPrivateSpace(preferences: Preferences) -> (LocalDocumentsRepository, URL?) {
        if let bookmark = preferences.data(.privateSpaceFolderBookmark) {
            if let resolved = PrivateSpaceFolder.resolve(bookmark) {
                if resolved.isStale, let fresh = try? PrivateSpaceFolder.bookmark(for: resolved.url) {
                    preferences.set(fresh, for: .privateSpaceFolderBookmark)
                }
                if let repository = try? LocalDocumentsRepository.inUserFolder(resolved.url) {
                    return (repository, resolved.url)
                }
            }
            preferences.remove(.privateSpaceFolderBookmark)
        }
        return (LocalDocumentsRepository(), nil)
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
