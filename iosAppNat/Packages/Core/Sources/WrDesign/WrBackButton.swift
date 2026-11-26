import SwiftUI

/// A way back at the top of a screen, like the arrow of the auth screens of the Compose app.
/// On iOS the navigation bar has the cancel action; a Mac window has no place for it, so the
/// screen shows this instead.
public struct WrBackButton: View {
    private let title: LocalizedStringKey
    private let action: () -> Void

    public init(_ title: LocalizedStringKey, action: @escaping () -> Void) {
        self.title = title
        self.action = action
    }

    @Environment(\.wrUsesV4) private var usesV4

    public var body: some View {
        Group {
            if usesV4 {
                // v4: a ghost button with an arrow, quiet next to the card.
                Button(action: action) {
                    Label(title, systemImage: "arrow.left")
                }
                .buttonStyle(WrButtonStyle(kind: .ghost))
            } else {
                Button(action: action) {
                    Label(title, systemImage: "chevron.backward")
                        .font(.callout.weight(.semibold))
                }
                .buttonStyle(.plain)
                .foregroundStyle(WrColors.accent)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .keyboardShortcut(.cancelAction)
    }
}
