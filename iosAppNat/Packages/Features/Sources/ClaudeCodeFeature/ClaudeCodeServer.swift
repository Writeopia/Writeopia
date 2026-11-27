import Foundation
import Observation
import WriteopiaUI
import WrSession
#if os(macOS)
import Network
#endif

/// The MCP server of the Mac app, for Claude Code: `POST /mcp` on 127.0.0.1 only, while the switch
/// of the settings is on. No secret: what reaches 127.0.0.1 runs on this computer. Web pages are
/// refused: an `Origin` that is not this machine gets 403, and so does a `Host` that is not
/// 127.0.0.1/localhost (a site that points its own name at 127.0.0.1, DNS rebinding). Mirrors `ClaudeCodeServer.kt` of the Compose app, on the same port, so
/// one `claude mcp add` works with whichever app runs.
@Observable
public final class ClaudeCodeServer {
    public static let shared = ClaudeCodeServer()

    /// The same port as the Compose desktop app.
    public static let port: UInt16 = 47615

    public enum State: Equatable {
        case off
        case running
        case failed(String)
    }

    /// Set by the listener; tests set it to answer requests without a socket.
    public internal(set) var state: State = .off

    /// Whether this platform has the server: only the Mac.
    public static var isAvailable: Bool {
        #if os(macOS)
        true
        #else
        false
        #endif
    }

    @ObservationIgnored private var handler: McpHandler?
    @ObservationIgnored private let defaults: UserDefaults
    #if os(macOS)
    @ObservationIgnored private var listener: NWListener?
    /// The connections of the current listener, cancelled when the server stops.
    @ObservationIgnored private var connections: [ObjectIdentifier: NWConnection] = [:]
    #endif

    private static let enabledKey = "wr.claudeCode.enabled"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public var isEnabled: Bool { state != .off }

    /// What the person runs once in a terminal: the Writeopia plugin of Claude Code (the tools
    /// and /writeopia:plan), from the marketplace repository Writeopia/writeopia-claude.
    public var pluginCommand: String? {
        guard state == .running else { return nil }
        return "claude plugin install writeopia --marketplace Writeopia/writeopia-claude"
    }

    /// Hands the server the session it writes through; it starts right away when the person had
    /// turned it on. Called once the app shows a workspace.
    public func configure(session: AppSession, appVersion: String) {
        guard handler == nil else { return }
        handler = McpHandler(
            documents: LiveDocuments(session: { [weak session] in session }),
            serverVersion: appVersion
        )
        if defaults.bool(forKey: Self.enabledKey) { start() }
    }

    public func setEnabled(_ enabled: Bool) {
        defaults.set(enabled, forKey: Self.enabledKey)
        if enabled { start() } else { stop() }
    }

    static func isLocalOrigin(_ origin: String) -> Bool {
        origin.wholeMatch(of: /https?:\/\/(localhost|127\.0\.0\.1|\[::1\])(:\d+)?/) != nil
    }

    /// The name the request was sent to: this machine at the port, nothing else.
    static func isLocalHost(_ host: String) -> Bool {
        ["127.0.0.1:\(port)", "localhost:\(port)", "[::1]:\(port)"].contains(host)
    }

    /// The answer to `request`: the HTTP side of the server, apart from the socket.
    func respond(to request: HTTPRequest) async -> Data {
        // Turned off meanwhile: nothing reaches the documents.
        guard case .running = state else { return httpResponse(status: 503) }
        guard request.path == "/mcp" else { return httpResponse(status: 404) }
        guard request.method == "POST" else { return httpResponse(status: 405) }

        if let origin = request.headers["origin"], !Self.isLocalOrigin(origin) {
            return httpResponse(status: 403)
        }
        guard let host = request.headers["host"], Self.isLocalHost(host) else { return httpResponse(status: 403) }
        guard let handler else { return httpResponse(status: 503) }

        guard let answer = await handler.handle(request.body) else { return httpResponse(status: 202) }
        return httpResponse(status: 200, body: answer)
    }

    #if os(macOS)
    private func start() {
        guard listener == nil else { return }

        do {
            let parameters = NWParameters.tcp
            parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: Self.port)!)
            parameters.allowLocalEndpointReuse = true
            let listener = try NWListener(using: parameters)
            listener.newConnectionHandler = { [weak self, weak listener] connection in
                Task { @MainActor in
                    // A connection of a listener stopped meanwhile is closed, not served.
                    guard let self, let listener, listener === self.listener else { return connection.cancel() }
                    self.serve(connection)
                }
            }
            listener.stateUpdateHandler = { [weak self, weak listener] update in
                Task { @MainActor in
                    // An update of a listener stopped meanwhile says nothing of the current one.
                    guard let self, let listener, listener === self.listener else { return }
                    self.listenerChanged(update)
                }
            }
            self.listener = listener
            listener.start(queue: .main)
        } catch {
            state = .failed(Self.portTaken(error))
        }
    }

    private func listenerChanged(_ update: NWListener.State) {
        switch update {
        case .ready:
            state = .running
        case let .failed(error):
            listener?.cancel()
            listener = nil
            state = .failed(Self.portTaken(error))
        default:
            break
        }
    }

    private static func portTaken(_ error: Error) -> String {
        String(localized: "The port \(String(port)) is taken (\(error.localizedDescription)). Is the other Writeopia app running with Claude Code on?")
    }

    private func stop() {
        LiveEditors.shared.reset()
        listener?.cancel()
        listener = nil
        connections.values.forEach { $0.cancel() }
        connections = [:]
        state = .off
    }

    private func serve(_ connection: NWConnection) {
        let id = ObjectIdentifier(connection)
        connections[id] = connection
        connection.stateUpdateHandler = { [weak self] update in
            switch update {
            case .cancelled, .failed:
                Task { @MainActor in self?.connections[id] = nil }
            default:
                break
            }
        }
        connection.start(queue: .main)
        receive(on: connection, buffer: Data())
    }

    private func receive(on connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) { [weak self] data, _, isComplete, error in
            Task { @MainActor in
                guard let self else { return connection.cancel() }
                var buffer = buffer
                if let data { buffer.append(data) }

                switch HTTPRequest.parse(buffer) {
                case let .request(request):
                    let answer = await self.respond(to: request)
                    connection.send(content: answer, completion: .contentProcessed { _ in connection.cancel() })
                case .invalid:
                    connection.send(content: httpResponse(status: 400), completion: .contentProcessed { _ in connection.cancel() })
                case .incomplete:
                    if isComplete || error != nil {
                        connection.cancel()
                    } else {
                        self.receive(on: connection, buffer: buffer)
                    }
                }
            }
        }
    }
    #else
    private func start() {}

    private func stop() {
        state = .off
    }
    #endif
}
