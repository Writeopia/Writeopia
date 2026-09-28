import SwiftUI
import WrDesign
import WrModels

/// Where the folder shown is: the workspace, the folders it's inside and the folder itself.
/// Tapping one of them goes back to it.
struct FolderBreadcrumb: View {
    let rootTitle: String
    let ancestors: [Folder]
    let current: String
    /// Called with the folder tapped, or nil for the root.
    let onSelect: (Folder?) -> Void

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 4) {
                    crumb(rootTitle, systemImage: "house") { onSelect(nil) }
                    ForEach(ancestors) { folder in
                        separator
                        crumb(folder.displayTitle, systemImage: nil) { onSelect(folder) }
                    }
                    separator
                    Text(current)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(WrColors.textLight)
                        .lineLimit(1)
                        .padding(.vertical, 6)
                        .id("current")
                        .accessibilityAddTraits(.isHeader)
                }
                .padding(.horizontal)
            }
            // The folder shown stays visible when the path is longer than the screen.
            .onAppear { proxy.scrollTo("current", anchor: .trailing) }
            .onChange(of: current) { proxy.scrollTo("current", anchor: .trailing) }
            .onChange(of: ancestors.map(\.id)) { proxy.scrollTo("current", anchor: .trailing) }
        }
        .background(WrColors.background)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("Folder path"))
        .accessibilityIdentifier("documents.breadcrumb")
    }

    private var separator: some View {
        Image(systemName: "chevron.right")
            .font(.caption2.weight(.semibold))
            .foregroundStyle(WrColors.textLighter)
            .accessibilityHidden(true)
    }

    private func crumb(_ title: String, systemImage: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.caption)
                }
                Text(title)
                    .lineLimit(1)
            }
            .font(.footnote)
            .foregroundStyle(WrColors.accent)
            .padding(.vertical, 6)
        }
        .buttonStyle(.plain)
    }
}
