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

    public var body: some View {
        Button(action: action) {
            Label(title, systemImage: "chevron.backward")
                .font(.callout.weight(.semibold))
        }
        .buttonStyle(.plain)
        .foregroundStyle(WrColors.accent)
        .frame(maxWidth: .infinity, alignment: .leading)
        .keyboardShortcut(.cancelAction)
    }
}
