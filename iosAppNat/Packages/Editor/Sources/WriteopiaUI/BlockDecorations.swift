import SwiftUI
import WrDesign

/// How a kind of block looks: its fill, its edge, the accent line of a card and the room around
/// its text. The steps of a block and the spaces between them share it, so a block of several
/// steps reads as one piece, like `DefaultTagDecoration` and `CodeBlockDrawer` of the SDK.
struct BlockStyle {
    let background: Color
    let border: Color?
    /// A line along the leading edge, inside the border.
    let accent: Color?
    let cornerRadius: CGFloat
    let leading: CGFloat
    let trailing: CGFloat
    let vertical: CGFloat

    nonisolated static let borderWidth: CGFloat = 1
    nonisolated static let accentWidth: CGFloat = 3
    /// Center of the accent line, from the leading edge.
    nonisolated static let accentOffset: CGFloat = 11.5

    init(kind: BlockKind) {
        switch kind {
        case .code:
            background = WrColors.secondaryFill
            border = nil
            accent = nil
            cornerRadius = 8
            leading = 10
            trailing = 10
            vertical = 8
        case .box:
            background = WrColors.accent.opacity(0.12)
            border = WrColors.accent.opacity(0.35)
            accent = nil
            cornerRadius = 10
            leading = 10
            trailing = 10
            vertical = 10
        case .card:
            background = WrColors.secondaryBackground
            border = WrColors.divider
            accent = WrColors.accent
            cornerRadius = 12
            // Room for the accent line before the text.
            leading = 24
            trailing = 12
            vertical = 12
        }
    }

    /// The room around the text of a step. The block goes on above a middle or last step and
    /// below a first or middle one, so those sides get none: the space between the steps is the
    /// gap.
    func insets(for position: BlockPosition) -> EdgeInsets {
        EdgeInsets(
            top: position.hasTopEdge ? vertical : 0,
            leading: leading,
            bottom: position.hasBottomEdge ? vertical : 0,
            trailing: trailing
        )
    }

    /// What the block adds beside the text of a step.
    var horizontalInsets: CGFloat { leading + trailing }

    /// The fill of a step: rounded only on the corners where the block starts or ends.
    func shape(for position: BlockPosition) -> UnevenRoundedRectangle {
        let top = position.hasTopEdge ? cornerRadius : 0
        let bottom = position.hasBottomEdge ? cornerRadius : 0
        return UnevenRoundedRectangle(
            topLeadingRadius: top,
            bottomLeadingRadius: bottom,
            bottomTrailingRadius: bottom,
            topTrailingRadius: top,
            style: .circular
        )
    }
}

/// The outline of a piece of a block: the side edges always, the top and bottom edges with their
/// rounded corners only where the block starts and ends. A space between two steps of the block
/// (`position == nil`) has the side edges only.
///
/// The stroke sits inside the bounds on the sides, and runs to the very top and bottom where the
/// block goes on, so the edges of consecutive pieces meet without a gap.
nonisolated struct BlockEdges: Shape {
    var position: BlockPosition?
    var cornerRadius: CGFloat

    func path(in rect: CGRect) -> Path {
        let half = BlockStyle.borderWidth / 2
        let hasTop = position?.hasTopEdge ?? false
        let hasBottom = position?.hasBottomEdge ?? false
        let minX = rect.minX + half
        let maxX = rect.maxX - half
        let minY = hasTop ? rect.minY + half : rect.minY
        let maxY = hasBottom ? rect.maxY - half : rect.maxY
        let radius = min(cornerRadius, (maxX - minX) / 2, (maxY - minY) / 2)

        var path = Path()

        if hasTop && hasBottom {
            path.addRoundedRect(
                in: CGRect(x: minX, y: minY, width: maxX - minX, height: maxY - minY),
                cornerSize: CGSize(width: radius, height: radius)
            )
        } else if hasTop {
            path.move(to: CGPoint(x: minX, y: maxY))
            path.addLine(to: CGPoint(x: minX, y: minY + radius))
            path.addArc(tangent1End: CGPoint(x: minX, y: minY), tangent2End: CGPoint(x: minX + radius, y: minY), radius: radius)
            path.addLine(to: CGPoint(x: maxX - radius, y: minY))
            path.addArc(tangent1End: CGPoint(x: maxX, y: minY), tangent2End: CGPoint(x: maxX, y: minY + radius), radius: radius)
            path.addLine(to: CGPoint(x: maxX, y: maxY))
        } else if hasBottom {
            path.move(to: CGPoint(x: minX, y: minY))
            path.addLine(to: CGPoint(x: minX, y: maxY - radius))
            path.addArc(tangent1End: CGPoint(x: minX, y: maxY), tangent2End: CGPoint(x: minX + radius, y: maxY), radius: radius)
            path.addLine(to: CGPoint(x: maxX - radius, y: maxY))
            path.addArc(tangent1End: CGPoint(x: maxX, y: maxY), tangent2End: CGPoint(x: maxX, y: maxY - radius), radius: radius)
            path.addLine(to: CGPoint(x: maxX, y: minY))
        } else {
            path.move(to: CGPoint(x: minX, y: minY))
            path.addLine(to: CGPoint(x: minX, y: maxY))
            path.move(to: CGPoint(x: maxX, y: minY))
            path.addLine(to: CGPoint(x: maxX, y: maxY))
        }

        return path
    }
}

/// The accent line of a card, along its leading edge. It stops short of the rounded corners
/// where the block starts and ends, and runs the full height everywhere else, through the spaces
/// between the steps too (`position == nil`).
nonisolated struct BlockAccentLine: Shape {
    var position: BlockPosition?
    var cornerRadius: CGFloat

    func path(in rect: CGRect) -> Path {
        let x = rect.minX + BlockStyle.accentOffset
        let top = (position?.hasTopEdge ?? false) ? rect.minY + cornerRadius : rect.minY
        let bottom = (position?.hasBottomEdge ?? false) ? rect.maxY - cornerRadius : rect.maxY
        var path = Path()
        guard bottom > top else { return path }
        path.move(to: CGPoint(x: x, y: top))
        path.addLine(to: CGPoint(x: x, y: bottom))
        return path
    }
}

/// The edge and the accent line of a piece of a block, over its fill.
struct BlockOutline: View {
    let style: BlockStyle
    var position: BlockPosition?

    var body: some View {
        ZStack {
            if let border = style.border {
                BlockEdges(position: position, cornerRadius: style.cornerRadius)
                    .stroke(border, lineWidth: BlockStyle.borderWidth)
            }
            if let accent = style.accent {
                BlockAccentLine(position: position, cornerRadius: style.cornerRadius)
                    .stroke(accent, style: StrokeStyle(lineWidth: BlockStyle.accentWidth, lineCap: .round))
            }
        }
    }
}

/// A step of a block on screen, as the step reports it to the editor.
struct BlockPiece {
    let groupId: String
    let kind: BlockKind
    let bounds: Anchor<CGRect>
}

/// The steps of the blocks on screen, collected up to the column of the editor.
struct BlockPiecesKey: PreferenceKey {
    static let defaultValue: [BlockPiece] = []

    static func reduce(value: inout [BlockPiece], nextValue: () -> [BlockPiece]) {
        value += nextValue()
    }
}

/// Paints every block of the document as one shape, from its first step to its last, behind the
/// steps. Painting the steps and the spaces one by one leaves hairlines between them where the
/// rows don't end on a pixel, so the block is painted once instead.
struct BlockPainter: View {
    let pieces: [BlockPiece]

    private struct Block: Identifiable {
        let id: String
        let kind: BlockKind
        var rect: CGRect
    }

    var body: some View {
        GeometryReader { proxy in
            ForEach(blocks(in: proxy)) { block in
                let style = BlockStyle(kind: block.kind)
                style.shape(for: .single)
                    .fill(style.background)
                    .overlay { BlockOutline(style: style, position: .single) }
                    .frame(width: block.rect.width, height: block.rect.height)
                    .offset(x: block.rect.minX, y: block.rect.minY)
            }
        }
    }

    /// The pieces joined by block, in the order of the document.
    private func blocks(in proxy: GeometryProxy) -> [Block] {
        var blocks: [Block] = []
        for piece in pieces {
            let rect = proxy[piece.bounds]
            if let index = blocks.firstIndex(where: { $0.id == piece.groupId }) {
                blocks[index].rect = blocks[index].rect.union(rect)
            } else {
                blocks.append(Block(id: piece.groupId, kind: piece.kind, rect: rect))
            }
        }
        return blocks
    }
}

extension View {
    /// The room a step keeps around its text inside its block.
    @ViewBuilder
    func blockInsets(_ block: BlockInfo?) -> some View {
        if let block {
            padding(BlockStyle(kind: block.kind).insets(for: block.position))
        } else {
            self
        }
    }

    /// Reports this view as a piece of `block` to the `BlockPainter` of the editor.
    func blockPiece(_ block: BlockInfo?) -> some View {
        anchorPreference(key: BlockPiecesKey.self, value: .bounds) { bounds in
            block.map { [BlockPiece(groupId: $0.groupId, kind: $0.kind, bounds: bounds)] } ?? []
        }
    }
}
