#if os(macOS)
import AppKit

/// Exported files go to a folder the user picks; the Mac has no share sheet for them.
@MainActor
enum ExportSaver {
    static func save(_ file: URL) {
        let panel = NSSavePanel()
        panel.nameFieldStringValue = file.lastPathComponent
        panel.canCreateDirectories = true
        panel.begin { response in
            guard response == .OK, let destination = panel.url else { return }
            try? FileManager.default.removeItem(at: destination)
            try? FileManager.default.copyItem(at: file, to: destination)
        }
    }
}
#endif
