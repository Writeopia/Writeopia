package io.writeopia.notes.desktop.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DesktopTabsStateTest {

    private fun state(initial: SavedTabs? = null, saves: MutableList<SavedTabs> = mutableListOf()): DesktopTabsState {
        var n = 0
        return DesktopTabsState(initial, newId = { "t${n++}" }, onChange = { saves += it })
    }

    @Test
    fun startsWithOneRootTab() {
        val tabs = state()
        assertEquals(listOf<TabPlace>(TabPlace.Root), tabs.snapshot().places)
        assertEquals(tabs.tabs.value.single().id, tabs.activeId.value)
    }

    @Test
    fun openAddsRightOfActiveAndActivates() {
        val tabs = state()
        val first = tabs.active
        val doc = tabs.open(TabPlace.Document("a", "A"))
        assertEquals(doc.id, tabs.activeId.value)

        tabs.activate(first.id)
        val folder = tabs.open(TabPlace.Folder("f"))
        assertEquals(listOf(first.id, folder.id, doc.id), tabs.tabs.value.map { it.id })
    }

    @Test
    fun openInBackgroundKeepsActive() {
        val tabs = state()
        val first = tabs.active
        tabs.open(TabPlace.Document("a", "A"), activate = false)
        assertEquals(first.id, tabs.activeId.value)
    }

    @Test
    fun closingActiveShowsRightNeighbourOrNewLast() {
        val tabs = state()
        val a = tabs.active
        val b = tabs.open(TabPlace.Folder("b"))
        val c = tabs.open(TabPlace.Folder("c"))

        tabs.activate(b.id)
        tabs.close(b.id)
        assertEquals(c.id, tabs.activeId.value)

        tabs.close(c.id)
        assertEquals(a.id, tabs.activeId.value)
    }

    @Test
    fun closingAnotherTabKeepsActive() {
        val tabs = state()
        val a = tabs.active
        val b = tabs.open(TabPlace.Folder("b"))
        tabs.close(a.id)
        assertEquals(b.id, tabs.activeId.value)
        assertEquals(listOf(b.id), tabs.tabs.value.map { it.id })
    }

    @Test
    fun closingLastTabLeavesNewRootTab() {
        val tabs = state()
        val only = tabs.active
        tabs.close(only.id)
        val fresh = tabs.tabs.value.single()
        assertNotEquals(only.id, fresh.id)
        assertEquals(TabPlace.Root, fresh.place)
        assertEquals(fresh.id, tabs.activeId.value)
    }

    @Test
    fun nextAndPreviousWrapAround() {
        val tabs = state()
        val a = tabs.active
        tabs.open(TabPlace.Folder("b"))
        val c = tabs.open(TabPlace.Folder("c"))

        tabs.next()
        assertEquals(a.id, tabs.activeId.value)
        tabs.previous()
        assertEquals(c.id, tabs.activeId.value)
    }

    @Test
    fun selectNineIsAlwaysTheLast() {
        val tabs = state()
        val a = tabs.active
        tabs.open(TabPlace.Folder("b"))
        val c = tabs.open(TabPlace.Folder("c"))

        tabs.select(0)
        assertEquals(a.id, tabs.activeId.value)
        tabs.select(8)
        assertEquals(c.id, tabs.activeId.value)
        tabs.select(5) // no such tab: nothing changes
        assertEquals(c.id, tabs.activeId.value)
    }

    @Test
    fun moveReorders() {
        val tabs = state()
        val a = tabs.active
        val b = tabs.open(TabPlace.Folder("b"))
        val c = tabs.open(TabPlace.Folder("c"))
        tabs.move(2, 0)
        assertEquals(listOf(c.id, a.id, b.id), tabs.tabs.value.map { it.id })
    }

    @Test
    fun findDocumentLooksAtCurrentPlaces() {
        val tabs = state()
        val doc = tabs.open(TabPlace.Document("a", "A"))
        assertEquals(doc, tabs.findDocument("a"))
        tabs.placeChanged(doc, TabPlace.Folder("f"))
        assertNull(tabs.findDocument("a"))
    }

    @Test
    fun restoresSavedTabsAndActiveIndex() {
        val saved = SavedTabs(
            places = listOf(TabPlace.Root, TabPlace.Document("a", "A"), TabPlace.Folder("f")),
            activeIndex = 1
        )
        val tabs = state(initial = saved)
        assertEquals(saved, tabs.snapshot())
        assertEquals(TabPlace.Document("a", "A"), tabs.active.place)
        assertEquals(TabPlace.Document("a", "A"), tabs.active.pendingPlace)
        assertNull(tabs.tabs.value.first().pendingPlace)
    }

    @Test
    fun outOfRangeActiveIndexIsClamped() {
        val tabs = state(initial = SavedTabs(listOf(TabPlace.Root, TabPlace.Folder("f")), activeIndex = 7))
        assertEquals(TabPlace.Folder("f"), tabs.active.place)
    }

    @Test
    fun everyChangeIsSaved() {
        val saves = mutableListOf<SavedTabs>()
        val tabs = state(saves = saves)
        val b = tabs.open(TabPlace.Folder("b"))
        tabs.placeChanged(b, TabPlace.Document("d", "D"))
        tabs.placeChanged(b, TabPlace.Document("d", "D")) // same place: no save
        tabs.previous()
        tabs.close(b.id)

        assertEquals(4, saves.size)
        assertEquals(SavedTabs(listOf(TabPlace.Root), 0), saves.last())
    }

    @Test
    fun jsonRoundTrip() {
        val saved = SavedTabs(
            places = listOf(TabPlace.Root, TabPlace.Folder("f"), TabPlace.Document("d", "Title \"q\""), TabPlace.Other),
            activeIndex = 2
        )
        assertEquals(saved, SavedTabs.fromJson(saved.toJson()))
        assertNull(SavedTabs.fromJson("not json"))
    }
}
