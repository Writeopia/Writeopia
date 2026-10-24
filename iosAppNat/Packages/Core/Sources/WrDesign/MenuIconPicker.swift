import SwiftUI
import WrModels

/// The icon of a folder or a document, like `IconsPicker` of the Compose app: the colors on
/// top, then the icons. A choice applies right away, so the sidebar, the cards and the editor
/// show it while the picker is still open to try another one.
public struct MenuIconPicker: View {
    let initial: IconInfo?
    /// The button that takes the icon away: "No icon" for a document, the default folder for a
    /// folder.
    let removeTitle: LocalizedStringKey
    let removeIdentifier: String
    let onPick: (IconInfo?) -> Void
    /// Fixed for popovers; nil fills the width, like in the bottom sheet of phones.
    let width: CGFloat?
    @State private var iconName: String?
    @State private var tint: Int?

    public init(
        initial: IconInfo?,
        removeTitle: LocalizedStringKey = "No icon",
        removeIdentifier: String = "documentIcon.remove",
        width: CGFloat? = 300,
        onPick: @escaping (IconInfo?) -> Void
    ) {
        self.initial = initial
        self.width = width
        self.removeTitle = removeTitle
        self.removeIdentifier = removeIdentifier
        self.onPick = onPick
        _iconName = State(initialValue: initial?.label)
        // Without a saved tint the first color is selected, like the Compose app, so the
        // picker always shows which color an icon gets.
        _tint = State(initialValue: initial?.tint ?? MenuIcons.tints.first)
    }

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: 8)

    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                ForEach(MenuIcons.tints, id: \.self) { tintButton($0) }
            }

            ScrollView {
                LazyVGrid(columns: columns, spacing: 4) {
                    ForEach(MenuIcons.all, id: \.name) { entry in
                        iconButton(entry.name, symbol: entry.symbol)
                    }
                }
            }
            .frame(height: 200)

            Button(removeTitle, role: .destructive) {
                iconName = nil
                onPick(nil)
            }
            .buttonStyle(.borderless)
            .disabled(iconName == nil)
            .accessibilityIdentifier(removeIdentifier)
        }
        .padding(12)
        .frame(width: width)
        .frame(maxWidth: width == nil ? .infinity : nil)
    }

    private func tintButton(_ value: Int) -> some View {
        let isSelected = tint == value
        return Button {
            tint = value
            // An icon already chosen takes the new color.
            if let iconName { onPick(IconInfo(label: iconName, tint: value)) }
        } label: {
            Circle()
                .fill(MenuIcons.color(for: value) ?? WrColors.accent)
                .frame(width: 18, height: 18)
                .overlay { Circle().strokeBorder(WrColors.divider, lineWidth: 1) }
                .padding(3)
                .overlay {
                    if isSelected { Circle().strokeBorder(WrColors.textLight, lineWidth: 2) }
                }
                .frame(maxWidth: .infinity)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("Color"))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func iconButton(_ name: String, symbol: String) -> some View {
        let isSelected = iconName == name
        return Button {
            iconName = name
            onPick(IconInfo(label: name, tint: tint))
        } label: {
            Image(systemName: symbol)
                .font(.body)
                .foregroundStyle(MenuIcons.color(for: tint) ?? WrColors.accent)
                .frame(maxWidth: .infinity, minHeight: 30)
                .background(
                    isSelected ? WrColors.accent.opacity(0.15) : .clear,
                    in: RoundedRectangle(cornerRadius: 8)
                )
                .contentShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
        .help(name)
        .accessibilityLabel(Text(name))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
