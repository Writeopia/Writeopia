import SwiftUI
import Writeopia
import WrDesign

/// Formatting of the selected text, floating above it, like `TextToolbox` of the Compose
/// desktop app: bold, italic, underline, link and the three highlights.
struct TextSelectionToolbar: View {
    let manager: WriteopiaStateManager

    static let height: CGFloat = 36

    var body: some View {
        HStack(spacing: 2) {
            spanButton(.bold, systemImage: "bold", label: "Bold")
            spanButton(.italic, systemImage: "italic", label: "Italic")
            spanButton(.underline, systemImage: "underline", label: "Underline")
            button(systemImage: "link", label: "Link", isActive: manager.isSpanActive(.link)) {
                manager.onLinkRequested?()
            }

            Divider()
                .frame(height: 18)
                .padding(.horizontal, 4)

            ForEach(Span.highlights, id: \.self) { span in
                Button {
                    manager.toggleSpan(span)
                } label: {
                    Circle()
                        .fill(color(of: span))
                        .frame(width: 16, height: 16)
                        .overlay {
                            Circle().strokeBorder(manager.isSpanActive(span) ? WrColors.accent : Color.clear, lineWidth: 2)
                        }
                        .frame(width: 26, height: 26)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .help(name(of: span))
                .accessibilityLabel(name(of: span))
            }
        }
        .padding(.horizontal, 6)
        .frame(height: Self.height)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(Color.primary.opacity(0.1)))
        .shadow(color: .black.opacity(0.18), radius: 10, y: 4)
        .accessibilityIdentifier("editor.textToolbar")
    }

    private func spanButton(_ span: Span, systemImage: String, label: LocalizedStringKey) -> some View {
        button(systemImage: systemImage, label: label, isActive: manager.isSpanActive(span)) {
            manager.toggleSpan(span)
        }
    }

    private func button(systemImage: String, label: LocalizedStringKey, isActive: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(isActive ? WrColors.accent : Color.primary)
                .frame(width: 26, height: 26)
                .background(isActive ? WrColors.accent.opacity(0.15) : Color.clear, in: RoundedRectangle(cornerRadius: 6))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .help(label)
        .accessibilityLabel(label)
    }

    private func color(of span: Span) -> Color {
        switch span {
        case .highlightGreen: .green.opacity(0.7)
        case .highlightRed: .red.opacity(0.65)
        default: .yellow.opacity(0.8)
        }
    }

    private func name(of span: Span) -> LocalizedStringKey {
        switch span {
        case .highlightGreen: "Green highlight"
        case .highlightRed: "Red highlight"
        default: "Yellow highlight"
        }
    }
}
