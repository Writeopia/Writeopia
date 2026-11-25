import SwiftUI
import WrDesign

/// A drawing inside the document, like `DrawingPreviewDrawer` of the Compose app: cropped to
/// its strokes, scaled down to fit the width (never up), and tappable to edit it.
public struct DrawingPreview: View {
    private let drawing: DrawingData?
    private let onTap: () -> Void
    /// The "..." of the drawing: the app opens its options (convert to an equation). Nil hides it.
    private let onOptions: (() -> Void)?
    /// The cloud AI is reading the drawing; a progress covers the preview.
    private let isConverting: Bool

    public init(json: String?, isConverting: Bool = false, onOptions: (() -> Void)? = nil, onTap: @escaping () -> Void) {
        drawing = DrawingData.fromJson(json)
        self.isConverting = isConverting
        self.onOptions = onOptions
        self.onTap = onTap
    }

    public var body: some View {
        Button(action: onTap) {
            content
                .frame(maxWidth: .infinity)
                .background(WrColors.systemBackground, in: RoundedRectangle(cornerRadius: 12))
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .strokeBorder(WrColors.divider)
                }
                .contentShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .overlay {
            if isConverting {
                RoundedRectangle(cornerRadius: 12)
                    .fill(WrColors.systemBackground.opacity(0.6))
                    .overlay { ProgressView() }
            }
        }
        // Over the button, so the Mac gets the click: a button inside the button wouldn't.
        .overlay(alignment: .topTrailing) {
            if let onOptions, !isConverting {
                Button(action: onOptions) {
                    Image(systemName: "ellipsis")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.primary)
                        .frame(width: 28, height: 28)
                        .background(.regularMaterial, in: Circle())
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .padding(8)
                .accessibilityLabel("Drawing options")
                .accessibilityIdentifier("drawing.options")
            }
        }
        .padding(.vertical, 4)
        .accessibilityLabel("Drawing")
        .accessibilityHint("Opens the drawing")
        .accessibilityIdentifier("drawing.preview")
    }

    @ViewBuilder
    private var content: some View {
        if let drawing, let bounds = drawing.bounds(), !drawing.isEmpty {
            GeometryReader { geometry in
                let scale = min(geometry.size.width / bounds.width, 1)
                Canvas { context, size in
                    let offsetX = (size.width - bounds.width * scale) / 2
                    let transform = CGAffineTransform(translationX: offsetX, y: 0)
                        .scaledBy(x: scale, y: scale)
                        .translatedBy(x: -bounds.minX, y: -bounds.minY)
                    StrokeRenderer.draw(
                        drawing.strokes,
                        in: &context,
                        background: WrColors.systemBackground,
                        transform: transform,
                        scale: scale
                    )
                }
            }
            .aspectRatio(bounds.width / bounds.height, contentMode: .fit)
            .frame(maxHeight: bounds.height)
            .padding(8)
        } else {
            // An empty drawing still leaves something to tap.
            Label("Empty drawing", systemImage: "pencil.and.scribble")
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
                .aspectRatio(16 / 9, contentMode: .fit)
        }
    }
}
