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

                switch server.state {
                case .running:
                    if let plugin = server.pluginCommand, let tools = server.command {
                        commandView(plugin: plugin, tools: tools)
                    }
                case let .failed(reason):
                    Text(reason).foregroundStyle(.red)
                case .off:
                    EmptyView()
                }
            } header: {
                Text("Claude Code")
            } footer: {
                Text("Let Claude Code write documents in this app: you see the text appear as it is written. It can only create documents and add to them, never change nor delete what is there.")
            }
        }
    }

    @ViewBuilder
    private func commandView(plugin: String, tools: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Run this once in a terminal to connect Claude Code:")
            commandText(plugin)
            HStack {
                copyButton(plugin)
                Button("New secret") {
                    server.renewToken()
                    copied = nil
                }
            }
            Text("The command holds a secret that lets programs on this computer write here. If it leaked, make a new one and run the new command.")
                .font(.caption)
                .foregroundStyle(.secondary)

            Text("Or connect only the tools, without /writeopia:plan:")
                .padding(.top, 8)
            commandText(tools)
            copyButton(tools)
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
