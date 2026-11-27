import SwiftUI
import WriteopiaUI
import WrModels

/// What the window of the app gives Claude Code, without importing the editor.
public enum ClaudeCode {
    /// Shows the document with this id and title in the editor, so the person watches Claude
    /// write into it.
    public static var openDocument: ((_ id: String, _ title: String) -> Void)? {
        get { LiveEditors.shared.openDocument }
        set { LiveEditors.shared.openDocument = newValue }
    }

    /// Shows a folder Claude made, so the person sees it appear.
    public static var openFolder: ((Folder) -> Void)?

    /// The folder the person has open: new folders and documents go there unless Claude says.
    public static var currentFolderId: (() -> String)?
}

/// The banner of AI mode, over the window: tells that Claude Code is at work and lets the person
/// stop it. Shows nothing while AI mode is off. Mirrors `ClaudeCodeAiModeBanner` of Compose.
public struct ClaudeCodeAiModeBanner: View {
    @State private var editors = LiveEditors.shared

    public init() {}

    public var body: some View {
        if editors.mode.isOn {
            HStack(spacing: 10) {
                ProgressView().controlSize(.small)
                Text(editors.mode == .writing ? "Claude Code is writing…" : "Claude Code is working…")
                Button("Stop") { editors.stop() }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
                    .accessibilityIdentifier("claudeCode.stop")
            }
            .padding(.leading, 14)
            .padding(.trailing, 6)
            .padding(.vertical, 6)
            .background(.regularMaterial, in: Capsule())
            .overlay(Capsule().strokeBorder(.tint, lineWidth: 1))
            .shadow(radius: 4, y: 2)
            .padding(.top, 10)
            .transition(.move(edge: .top).combined(with: .opacity))
            .accessibilityElement(children: .contain)
            .accessibilityLabel("Claude Code AI mode")
        }
    }
}
