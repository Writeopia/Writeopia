#if os(macOS)
import AppKit
#endif
import SwiftUI
import WrData
import WrDesign
import WrModels
import WrSession

/// The card at the bottom left of the graph: what the clicked node is and a button that goes to it.
enum GraphItemDetails: Equatable {
    case document(WrDocument, folderName: String?, tagNames: [String])
    case folder(Folder, folderCount: Int, documentCount: Int)
    case tag(DocumentTag, documentCount: Int)
}

/// The documents of the workspace as a force graph, grouped by folder or by tag, like the
/// Compose app: drag a node, pan on empty space, pinch to zoom, tap a node for its card.
struct DocumentsGraphView: View {
    @Environment(AppSession.self) private var session
    @Environment(\.colorScheme) private var colorScheme
    let onOpenDocument: (WrDocument) -> Void
    let onOpenFolder: (Folder) -> Void
    let onOpenTag: (DocumentTag) -> Void

    @State private var simulation = GraphSimulation(size: CGSize(width: 800, height: 600))
    @State private var contents = FolderContents()
    @State private var tags: [DocumentTag] = []
    @State private var grouping: GraphGrouping = .folders
    @State private var scale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var hoveredId: String?
    @State private var pinnedIds: Set<String> = []
    @State private var details: GraphItemDetails?
    @State private var draggedId: String?
    @State private var zoomAtStart: CGFloat?
    @State private var frame = 0

    var body: some View {
        GeometryReader { geometry in
            TimelineView(.animation) { timeline in
                Canvas { context, size in
                    _ = timeline.date
                    simulation.tick()
                    draw(in: &context, size: size)
                }
                .onChange(of: geometry.size, initial: true) {
                    simulation.size = geometry.size
                }
            }
            .contentShape(Rectangle())
            .gesture(dragGesture(in: geometry.size))
            .simultaneousGesture(magnifyGesture)
            .onTapGesture { location in tapped(at: location) }
            .onContinuousHover { phase in
                switch phase {
                case .active(let location): hoveredId = node(at: location)?.id
                case .ended: hoveredId = nil
                }
            }
            #if os(macOS)
            // Scrolling, with the wheel or two fingers, moves the graph in both directions.
            .overlay {
                ScrollWheelReader { delta in
                    offset.width += delta.width
                    offset.height += delta.height
                }
            }
            #endif
        }
        .background(WrColors.surface)
        .overlay(alignment: .topTrailing) {
            Picker("Grouping", selection: $grouping) {
                Text("Folders").tag(GraphGrouping.folders)
                Text("Tags").tag(GraphGrouping.tags)
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            .frame(maxWidth: 180)
            .padding(12)
            .accessibilityIdentifier("graph.grouping")
        }
        .overlay(alignment: .bottomTrailing) {
            VStack(spacing: 4) {
                zoomButton("plus") { scale = min(scale * 1.25, 4) }
                zoomButton("minus") { scale = max(scale / 1.25, 0.3) }
                zoomButton("1.circle") { scale = 1; offset = .zero }
            }
            .padding(12)
        }
        .overlay(alignment: .bottomLeading) {
            if let details {
                GraphItemCard(
                    details: details,
                    onOpenDocument: onOpenDocument,
                    onOpenFolder: onOpenFolder,
                    onOpenTag: onOpenTag,
                    onClose: { self.details = nil }
                )
                .padding(12)
            }
        }
        .navigationTitle("Documents graph")
        .task { await load() }
        .onChange(of: grouping) {
            details = nil
            rebuild()
        }
    }

    // MARK: - Data

    private func load() async {
        contents = (try? await session.documents.allContents()) ?? FolderContents()
        tags = (try? await session.documents.tags()) ?? []
        rebuild()
    }

    private func rebuild() {
        let built = grouping == .tags
            ? DocumentsGraphBuilder.byTags(documents: contents.documents, tags: tags)
            : DocumentsGraphBuilder.byFolders(contents)
        simulation.load(nodes: built.nodes, links: built.links)
        if built.nodes.count <= 12 {
            pinnedIds = Set(built.nodes.map(\.id))
        }
    }

    // MARK: - Drawing

    private var nodeRadius: CGFloat {
        simulation.nodes.count < 15 ? CGFloat(25 - simulation.nodes.count) : 8
    }

    private var center: CGPoint { CGPoint(x: simulation.size.width / 2, y: simulation.size.height / 2) }

    private func toScreen(_ point: CGPoint) -> CGPoint {
        CGPoint(x: (point.x - center.x) * scale + center.x + offset.width, y: (point.y - center.y) * scale + center.y + offset.height)
    }

    private func toGraph(_ point: CGPoint) -> CGPoint {
        CGPoint(x: (point.x - offset.width - center.x) / scale + center.x, y: (point.y - offset.height - center.y) / scale + center.y)
    }

    private func draw(in context: inout GraphicsContext, size: CGSize) {
        context.translateBy(x: offset.width, y: offset.height)
        context.translateBy(x: center.x, y: center.y)
        context.scaleBy(x: scale, y: scale)
        context.translateBy(x: -center.x, y: -center.y)

        for link in simulation.links {
            var path = Path()
            path.move(to: CGPoint(x: link.source.x, y: link.source.y))
            path.addLine(to: CGPoint(x: link.target.x, y: link.target.y))
            context.stroke(path, with: .color(.gray.opacity(0.5)), lineWidth: 2 / scale)
        }

        for node in simulation.nodes {
            let hovered = node.id == hoveredId
            var radius = node.data.isHub ? nodeRadius : nodeRadius / 2
            if hovered { radius *= 1.4 }
            let rect = CGRect(x: node.x - radius, y: node.y - radius, width: radius * 2, height: radius * 2)
            context.fill(Path(ellipseIn: rect), with: .color(node.isDragged ? .red : color(of: node.data)))
        }

        let highlighted = simulation.nodes.filter { $0.id == hoveredId || pinnedIds.contains($0.id) }
        let faded = simulation.nodes.filter { !($0.id == hoveredId || pinnedIds.contains($0.id)) }
        for node in faded { drawLabel(node, in: &context, highlighted: false) }
        for node in highlighted { drawLabel(node, in: &context, highlighted: true) }
    }

    private func drawLabel(_ node: GraphNode, in context: inout GraphicsContext, highlighted: Bool) {
        guard !node.data.label.isEmpty else { return }
        let text = Text(" \(node.data.label) ")
            .font(.system(size: (highlighted ? 10 : 8) / scale, weight: highlighted ? .bold : .regular))
            .foregroundStyle(Color.white.opacity(highlighted ? 1 : 0.75))
        let resolved = context.resolve(text)
        let size = resolved.measure(in: CGSize(width: 240, height: 60))
        let origin = CGPoint(x: node.x - 30, y: node.y + (node.data.isHub ? nodeRadius : nodeRadius / 2) + 4)
        let rect = CGRect(origin: origin, size: size)
        context.fill(Path(roundedRect: rect, cornerRadius: 2 / scale), with: .color(.blue.opacity(highlighted ? 1 : 0.45)))
        context.draw(resolved, in: rect)
    }

    private func color(of node: GraphNodeData) -> Color {
        switch node.kind {
        case .tag: Color(red: 1, green: 0.76, blue: 0.03)
        case .folder: Color(red: 0.13, green: 0.59, blue: 0.95)
        case .document: .gray
        }
    }

    private func zoomButton(_ systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .frame(width: 32, height: 28)
                .background(WrColors.secondaryFill, in: RoundedRectangle(cornerRadius: 6))
        }
        .buttonStyle(.plain)
    }

    // MARK: - Pointer

    private func node(at location: CGPoint, radius: CGFloat = 18) -> GraphNode? {
        let point = toGraph(location)
        return simulation.nodes.first { node in
            hypot(node.x - point.x, node.y - point.y) < radius
        }
    }

    private func dragGesture(in size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 2)
            .onChanged { value in
                if draggedId == nil, zoomAtStart == nil {
                    if let node = node(at: value.startLocation, radius: 14) {
                        draggedId = node.id
                        node.isDragged = true
                    } else {
                        draggedId = ""
                    }
                }
                if let id = draggedId, !id.isEmpty, let node = simulation.nodes.first(where: { $0.id == id }) {
                    let point = toGraph(value.location)
                    node.x = point.x
                    node.y = point.y
                    node.vx = 0
                    node.vy = 0
                } else if draggedId == "" {
                    offset = CGSize(width: offset.width + value.translation.width - lastTranslation.width,
                                    height: offset.height + value.translation.height - lastTranslation.height)
                    lastTranslation = value.translation
                }
            }
            .onEnded { _ in
                if let id = draggedId, let node = simulation.nodes.first(where: { $0.id == id }) {
                    node.isDragged = false
                }
                draggedId = nil
                lastTranslation = .zero
            }
    }

    @State private var lastTranslation: CGSize = .zero

    private var magnifyGesture: some Gesture {
        MagnifyGesture()
            .onChanged { value in
                if zoomAtStart == nil { zoomAtStart = scale }
                scale = min(max((zoomAtStart ?? 1) * value.magnification, 0.3), 4)
            }
            .onEnded { _ in zoomAtStart = nil }
    }

    private func tapped(at location: CGPoint) {
        guard let node = node(at: location, radius: 20) else { return }
        if pinnedIds.contains(node.id) { pinnedIds.remove(node.id) } else { pinnedIds.insert(node.id) }
        details = detailsOf(node.data)
    }

    private func detailsOf(_ node: GraphNodeData) -> GraphItemDetails? {
        switch node.kind {
        case .tag:
            guard let tag = tags.first(where: { $0.id == node.id }) else { return nil }
            return .tag(tag, documentCount: contents.documents.filter { $0.tagIds.contains(tag.id) }.count)
        case .folder:
            guard let folder = contents.folders.first(where: { $0.id == node.id }) else { return nil }
            return .folder(
                folder,
                folderCount: contents.folders.filter { $0.parentId == folder.id }.count,
                documentCount: contents.documents.filter { $0.parentId == folder.id }.count
            )
        case .document:
            guard let document = contents.documents.first(where: { $0.id == node.id }) else { return nil }
            let folderName = contents.folders.first { $0.id == document.parentId }?.displayTitle
            let tagNames = document.tagIds.compactMap { id in tags.first { $0.id == id }?.name }
            return .document(document, folderName: folderName, tagNames: tagNames)
        }
    }
}

private struct GraphItemCard: View {
    let details: GraphItemDetails
    let onOpenDocument: (WrDocument) -> Void
    let onOpenFolder: (Folder) -> Void
    let onOpenTag: (DocumentTag) -> Void
    let onClose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(kind).font(.caption).foregroundStyle(.secondary)
                    Text(title).font(.headline).lineLimit(2)
                }
                Spacer()
                Button(action: onClose) {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close")
            }
            ForEach(facts, id: \.self) { fact in
                Text(fact).font(.footnote).foregroundStyle(.secondary).lineLimit(2)
            }
            Button(action: open) {
                Text(buttonTitle).frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("graph.card.open")
        }
        .padding(16)
        .frame(minWidth: 220, maxWidth: 320)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(0.1), radius: 8, y: 2)
        .accessibilityIdentifier("graph.card")
    }

    private var kind: LocalizedStringKey {
        switch details {
        case .document: "Document"
        case .folder: "Folder"
        case .tag: "Tag"
        }
    }

    private var title: String {
        switch details {
        case .document(let document, _, _): document.displayTitle
        case .folder(let folder, _, _): folder.displayTitle
        case .tag(let tag, _): tag.name
        }
    }

    private var facts: [String] {
        switch details {
        case .document(let document, let folderName, let tagNames):
            [
                String(localized: "Updated \(document.lastUpdatedDate.formatted(date: .abbreviated, time: .omitted))"),
                folderName.map { String(localized: "In \($0)") } ?? String(localized: "At the root"),
                tagNames.isEmpty ? String(localized: "No tags") : String(localized: "Tags: \(tagNames.joined(separator: ", "))"),
            ]
        case .folder(_, let folderCount, let documentCount):
            [String(localized: "\(folderCount) folders, \(documentCount) documents")]
        case .tag(let tag, let documentCount):
            (tag.description.isEmpty ? [] : [tag.description]) + [String(localized: "\(documentCount) documents")]
        }
    }

    private var buttonTitle: LocalizedStringKey {
        switch details {
        case .document: "Open document"
        case .folder: "Open folder"
        case .tag: "Show its documents"
        }
    }

    private func open() {
        switch details {
        case .document(let document, _, _): onOpenDocument(document)
        case .folder(let folder, _, _): onOpenFolder(folder)
        case .tag(let tag, _): onOpenTag(tag)
        }
    }
}

#if os(macOS)
/// Reports the scroll wheel (or two-finger scroll) over its area. SwiftUI has no gesture for
/// it, so an AppKit event monitor is used; the view itself stays out of hit testing, so the
/// taps and drags of the canvas under it keep working.
private struct ScrollWheelReader: NSViewRepresentable {
    let onScroll: (CGSize) -> Void

    func makeNSView(context: Context) -> ScrollWheelCatcher {
        let view = ScrollWheelCatcher()
        view.onScroll = onScroll
        return view
    }

    func updateNSView(_ view: ScrollWheelCatcher, context: Context) {
        view.onScroll = onScroll
    }
}

final class ScrollWheelCatcher: NSView {
    var onScroll: ((CGSize) -> Void)?
    private var monitor: Any?

    override func viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        if let monitor {
            NSEvent.removeMonitor(monitor)
            self.monitor = nil
        }
        guard window != nil else { return }
        monitor = NSEvent.addLocalMonitorForEvents(matching: .scrollWheel) { [weak self] event in
            guard let self, let window = self.window, event.window === window else { return event }
            let point = self.convert(event.locationInWindow, from: nil)
            guard self.bounds.contains(point) else { return event }
            // Wheel notches are coarse; the trackpad reports points already.
            let factor: CGFloat = event.hasPreciseScrollingDeltas ? 1 : 10
            self.onScroll?(CGSize(width: event.scrollingDeltaX * factor, height: event.scrollingDeltaY * factor))
            return nil
        }
    }

    override func hitTest(_ point: NSPoint) -> NSView? { nil }
}
#endif
