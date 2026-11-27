import Foundation
import Testing
@testable import WriteopiaUI
import Writeopia
import WrModels

/// The editors of the Mac tabs: the tab the person moves to is the one Claude Code writes into.
@MainActor
@Suite struct LiveEditorsTabsTests {
    private final class Tab {}

    private func manager(_ id: String) -> WriteopiaStateManager {
        let manager = WriteopiaStateManager()
        manager.loadDocument(WrDocument(id: id, title: "", workspaceId: "w", content: []))
        return manager
    }

    @Test func bringingATabToFrontMakesItsEditorCurrent() {
        let editors = LiveEditors()
        let first = Tab()
        let second = Tab()

        editors.attach(documentId: "a", manager: manager("a"), owner: first)
        editors.attach(documentId: "b", manager: manager("b"), owner: second)
        #expect(editors.current?.documentId == "b")

        editors.bringToFront(owner: first)
        #expect(editors.current?.documentId == "a")
        // The other tab's editor stays attached, out of sight.
        #expect(editors.editors.map(\.documentId) == ["b", "a"])
    }

    @Test func aTabWithoutEditorsChangesNothing() {
        let editors = LiveEditors()
        let tab = Tab()
        editors.attach(documentId: "a", manager: manager("a"), owner: tab)

        editors.bringToFront(owner: Tab())
        #expect(editors.current?.documentId == "a")
    }

    @Test func theEditorsOfATabKeepTheirOrder() {
        let editors = LiveEditors()
        let first = Tab()
        let second = Tab()

        editors.attach(documentId: "a1", manager: manager("a1"), owner: first)
        editors.attach(documentId: "b", manager: manager("b"), owner: second)
        editors.attach(documentId: "a2", manager: manager("a2"), owner: first)

        editors.bringToFront(owner: second)
        editors.bringToFront(owner: first)
        #expect(editors.editors.map(\.documentId) == ["b", "a1", "a2"])
    }
}
