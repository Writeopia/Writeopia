import Foundation
import WrModels

/// What the hubs of the documents graph are: the folders of the workspace, or its tags.
public enum GraphGrouping: String, CaseIterable, Identifiable, Sendable {
    case folders
    case tags

    public var id: String { rawValue }
}

/// A node of the graph before the simulation places it.
public struct GraphNodeData: Equatable, Identifiable, Sendable {
    public enum Kind: Sendable { case folder, tag, document }

    public let id: String
    public let label: String
    public let kind: Kind

    public init(id: String, label: String, kind: Kind) {
        self.id = id
        self.label = label
        self.kind = kind
    }

    public var isHub: Bool { kind != .document }
}

public struct GraphLinkData: Equatable, Hashable, Sendable {
    public let source: String
    public let target: String

    public init(source: String, target: String) {
        self.source = source
        self.target = target
    }
}

/// Builds the nodes and links of the graph, like `GraphRepository` and `toGraph` of the Kotlin
/// app: the root is never a node, an item under several hubs is one node with one link per hub.
public enum DocumentsGraphBuilder {
    public static func byFolders(_ contents: FolderContents) -> (nodes: [GraphNodeData], links: [GraphLinkData]) {
        var nodes: [GraphNodeData] = []
        var seen: Set<String> = []
        for folder in contents.folders where seen.insert(folder.id).inserted {
            nodes.append(GraphNodeData(id: folder.id, label: folder.title, kind: .folder))
        }
        for document in contents.documents where seen.insert(document.id).inserted {
            nodes.append(GraphNodeData(id: document.id, label: document.title, kind: .document))
        }

        var links: [GraphLinkData] = []
        for folder in contents.folders where seen.contains(folder.parentId) {
            links.append(GraphLinkData(source: folder.parentId, target: folder.id))
        }
        for document in contents.documents {
            if let parent = document.parentId, seen.contains(parent) {
                links.append(GraphLinkData(source: parent, target: document.id))
            }
        }
        return (nodes, links)
    }

    /// Every live tag is a hub, each document hangs from each of its tags, folders don't appear.
    public static func byTags(documents: [WrDocument], tags: [DocumentTag]) -> (nodes: [GraphNodeData], links: [GraphLinkData]) {
        let live = tags.filter { !$0.deleted }
        var nodes = live.map { GraphNodeData(id: $0.id, label: $0.name, kind: .tag) }
        var seen = Set(live.map(\.id))
        for document in documents where seen.insert(document.id).inserted {
            nodes.append(GraphNodeData(id: document.id, label: document.title, kind: .document))
        }

        var links: [GraphLinkData] = []
        for document in documents {
            for tagId in document.tagIds where seen.contains(tagId) {
                links.append(GraphLinkData(source: tagId, target: document.id))
            }
        }
        return (nodes, links)
    }
}

/// A node placed by the simulation, with its velocity.
public final class GraphNode: Identifiable {
    public let data: GraphNodeData
    public var x: CGFloat
    public var y: CGFloat
    public var vx: CGFloat = 0
    public var vy: CGFloat = 0
    public var isDragged = false

    public var id: String { data.id }

    init(data: GraphNodeData, x: CGFloat, y: CGFloat) {
        self.data = data
        self.x = x
        self.y = y
    }
}

/// The force layout of the Kotlin `DocumentsGraphViewModel`: links pull to a fixed distance,
/// nodes push each other away, everything drifts to the centre, the motion is damped, and nodes
/// closer than `collisionDistance` are pushed apart so their names stay readable.
public final class GraphSimulation {
    static let linkDistance: CGFloat = 100
    static let chargeStrength: CGFloat = 12000
    static let collisionDistance: CGFloat = 64

    public private(set) var nodes: [GraphNode] = []
    public private(set) var links: [(source: GraphNode, target: GraphNode)] = []
    public var size: CGSize

    public init(size: CGSize) {
        self.size = size
    }

    /// Replaces the graph; nodes that were there keep their place, so switching back doesn't jump.
    public func load(nodes data: [GraphNodeData], links linkData: [GraphLinkData]) {
        let previous = Dictionary(nodes.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        nodes = data.map { item in
            if let old = previous[item.id] {
                return GraphNode(data: item, x: old.x, y: old.y)
            }
            return GraphNode(
                data: item,
                x: CGFloat.random(in: 0.3...0.7) * size.width,
                y: CGFloat.random(in: 0.3...0.7) * size.height
            )
        }
        let byId = Dictionary(nodes.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        links = linkData.compactMap { link in
            guard let source = byId[link.source], let target = byId[link.target] else { return nil }
            return (source, target)
        }
    }

    public func tick(_ dt: CGFloat = 0.016) {
        applyLinkForce()
        applyChargeForce()
        applyCenteringForce()
        updatePositions(dt)
        applyCollision()
    }

    private func applyLinkForce() {
        let linkDistance = Self.linkDistance
        let strength: CGFloat = 0.15
        for link in links {
            let dx = link.target.x - link.source.x
            let dy = link.target.y - link.source.y
            let distance = max(sqrt(dx * dx + dy * dy), 0.01)
            let force = (distance - linkDistance) * strength
            let fx = force * dx / distance
            let fy = force * dy / distance
            link.source.vx += fx
            link.source.vy += fy
            link.target.vx -= fx
            link.target.vy -= fy
        }
    }

    private func applyChargeForce() {
        let chargeStrength = Self.chargeStrength
        for i in nodes.indices {
            for j in (i + 1)..<max(i + 1, nodes.count) {
                let a = nodes[i]
                let b = nodes[j]
                let dx = b.x - a.x
                let dy = b.y - a.y
                let distanceSq = max(dx * dx + dy * dy, 0.01)
                let force = chargeStrength / distanceSq
                let distance = sqrt(distanceSq)
                let fx = force * dx / distance
                let fy = force * dy / distance
                a.vx -= fx
                a.vy -= fy
                b.vx += fx
                b.vy += fy
            }
        }
    }

    private func applyCenteringForce() {
        let strength: CGFloat = 0.002
        let centerX = size.width / 2
        let centerY = size.height / 2
        for node in nodes {
            node.vx += (centerX - node.x) * strength
            node.vy += (centerY - node.y) * strength
        }
    }

    private func updatePositions(_ dt: CGFloat) {
        let damping: CGFloat = 0.95
        for node in nodes where !node.isDragged {
            node.vx *= damping
            node.vy *= damping
            node.x += node.vx * dt
            node.y += node.vy * dt
        }
    }

    /// Moves the positions, after the velocities, like a d3 collide.
    private func applyCollision() {
        let minDistance = Self.collisionDistance
        for i in nodes.indices {
            for j in (i + 1)..<max(i + 1, nodes.count) {
                let a = nodes[i]
                let b = nodes[j]
                let dx = b.x - a.x
                let dy = b.y - a.y
                let distance = max(sqrt(dx * dx + dy * dy), 0.01)
                guard distance < minDistance else { continue }
                let push = (minDistance - distance) / 4
                let px = push * dx / distance
                let py = push * dy / distance
                if !a.isDragged {
                    a.x -= px
                    a.y -= py
                }
                if !b.isDragged {
                    b.x += px
                    b.y += py
                }
            }
        }
    }
}
