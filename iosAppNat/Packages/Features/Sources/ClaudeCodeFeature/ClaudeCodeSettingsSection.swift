import SwiftUI
import WrDesign
#if os(macOS)
import AppKit
#endif

/// The Claude Code part of the AI settings: the switch of the local MCP server and the command
/// that connects Claude Code to it. Shows nothing where the app has no server (only the Mac has).
public struct ClaudeCodeSettingsSection: View {
    @State private var server = ClaudeCodeServer.shared
    @State private var copied: String?

    public init() {}

    public var body: some View {
        if ClaudeCodeServer.isAvailable {
            Section {
                Toggle("Allow Claude Code to write", isOn: Binding(
                    get: { server.isEnabled },
                    set: { server.setEnabled($0) }
                ))
                .accessibilityIdentifier("settings.claudeCode.enabled")

                // What turning it on means for privacy: the documents reach the AI service Claude Code uses.
                Label {
                    Text("While this is on, Claude Code can search, read and write the documents of the space open in the app, the private space too. What it reads is sent to the AI service Claude Code uses (Anthropic, unless you set up another one), so these documents are no longer private. Turn it off to keep them private.")
                } icon: {
                    Image(systemName: "lock.open")
                }
                .font(.callout)
                .foregroundStyle(.secondary)
                .accessibilityIdentifier("settings.claudeCode.privacy")

                switch server.state {
                case .running:
                    if let plugin = server.pluginCommand { commandView(plugin: plugin) }
                case let .failed(reason):
                    Text(reason).foregroundStyle(.red)
                case .off:
                    EmptyView()
                }
            } header: {
                Text("Claude Code")
            } footer: {
                Text("Let Claude Code search, read and write documents in this app: you see the text appear as it is written. It can create documents and add to them, never change nor delete what is there.")
            }
        }
    }

    @ViewBuilder
    private func commandView(plugin: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Run this once in a terminal to connect Claude Code:")
            commandText(plugin)
            copyButton(plugin)
        }
        .padding(.vertical, 4)
    }

    private func commandText(_ command: String) -> some View {
        Text(command)
            .font(.system(.caption, design: .monospaced))
            .textSelection(.enabled)
            .padding(8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.quaternary, in: RoundedRectangle(cornerRadius: 6))
    }

    private func copyButton(_ command: String) -> some View {
        Button(copied == command ? "Copied" : "Copy") {
            copy(command)
            copied = command
        }
    }

    private func copy(_ text: String) {
        #if os(macOS)
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(text, forType: .string)
        #endif
    }
}
