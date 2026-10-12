import Foundation
import Testing
@testable import DocumentsFeature
import WrModels

@Suite struct GraphBuilderTests {
    private let folder = Folder(id: "f", parentId: "root", title: "F", workspaceId: "w")
    private let sub = Folder(id: "g", parentId: "f", title: "G", workspaceId: "w")
    private let a = WrDocument(id: "a", title: "A", workspaceId: "w", parentId: "f", tagIds: ["work", "ideas"])
    private let b = WrDocument(id: "b", title: "B", workspaceId: "w", parentId: "root", tagIds: ["work"])
    private let c = WrDocument(id: "c", title: "C", workspaceId: "w", parentId: "root")

    @Test func byFoldersLinksChildrenToTheirFolderAndNeverToTheRoot() {
        let graph = DocumentsGraphBuilder.byFolders(FolderContents(folders: [folder, sub], documents: [a, b, c]))

        #expect(graph.nodes.map(\.id) == ["f", "g", "a", "b", "c"])
        #expect(graph.nodes.filter(\.isHub).map(\.id) == ["f", "g"])
        #expect(Set(graph.links) == [GraphLinkData(source: "f", target: "g"), GraphLinkData(source: "f", target: "a")])
    }

    @Test func byTagsMakesOneNodePerDocumentWithOneLinkPerTag() {
        let tags = [
            DocumentTag(id: "work", workspaceId: "w", name: "Work"),
            DocumentTag(id: "ideas", workspaceId: "w", name: "Ideas"),
            DocumentTag(id: "gone", workspaceId: "w", name: "Gone", deleted: true),
        ]
        let graph = DocumentsGraphBuilder.byTags(documents: [a, b, c], tags: tags)

        #expect(graph.nodes.map(\.id) == ["work", "ideas", "a", "b", "c"])
        #expect(graph.nodes.first { $0.id == "work" }?.kind == .tag)
        #expect(Set(graph.links) == [
            GraphLinkData(source: "work", target: "a"), GraphLinkData(source: "ideas", target: "a"),
            GraphLinkData(source: "work", target: "b"),
        ])
    }

    @Test func theSimulationPullsLinkedNodesTogetherAndKeepsPlacesAcrossReloads() {
        let simulation = GraphSimulation(size: CGSize(width: 400, height: 400))
        simulation.load(
            nodes: [GraphNodeData(id: "x", label: "x", kind: .folder), GraphNodeData(id: "y", label: "y", kind: .document)],
            links: [GraphLinkData(source: "x", target: "y")]
        )
        simulation.nodes[0].x = 0; simulation.nodes[0].y = 200
        simulation.nodes[1].x = 400; simulation.nodes[1].y = 200
        for _ in 0..<200 { simulation.tick() }
        let distance = hypot(simulation.nodes[0].x - simulation.nodes[1].x, simulation.nodes[0].y - simulation.nodes[1].y)
        #expect(distance < 200)

        let before = (simulation.nodes[0].x, simulation.nodes[0].y)
        simulation.load(nodes: [GraphNodeData(id: "x", label: "x", kind: .folder)], links: [])
        #expect(simulation.nodes[0].x == before.0 && simulation.nodes[0].y == before.1)
    }
}
