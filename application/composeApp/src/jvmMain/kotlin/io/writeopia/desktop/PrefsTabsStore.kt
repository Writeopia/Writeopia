package io.writeopia.desktop

import io.writeopia.notes.desktop.tabs.SavedTabs
import io.writeopia.notes.desktop.tabs.TabPlace
import io.writeopia.notes.desktop.tabs.TabsStore
import java.util.prefs.Preferences

/**
 * The open tabs of each workspace, in the Java preferences (node `io/writeopia/tabs`), like the
 * Claude Code switch. A preference value holds at most 8 KB, so the titles saved (only shown
 * until the document loads) are cut short, and the tabs past what fits are not kept.
 */
class PrefsTabsStore(
    private val prefs: Preferences = Preferences.userRoot().node("io/writeopia/tabs"),
) : TabsStore {

    override fun load(workspaceId: String): SavedTabs? =
        runCatching { prefs.get(key(workspaceId), null) }.getOrNull()?.let(SavedTabs::fromJson)

    override fun save(workspaceId: String, tabs: SavedTabs) {
        runCatching { prefs.put(key(workspaceId), fitting(tabs)) }
    }

    private fun fitting(tabs: SavedTabs): String {
        var places = tabs.places.map { place ->
            if (place is TabPlace.Document) place.copy(title = place.title.take(TITLE_LENGTH)) else place
        }
        var json = SavedTabs(places, tabs.activeIndex).toJson()

        while (json.length > Preferences.MAX_VALUE_LENGTH && places.size > 1) {
            places = places.dropLast(1)
            json = SavedTabs(places, tabs.activeIndex.coerceAtMost(places.lastIndex)).toJson()
        }
        return json
    }

    // Keys are at most 80 characters; workspace ids are far shorter.
    private fun key(workspaceId: String) = "workspace.${workspaceId.take(60)}"

    private companion object {
        const val TITLE_LENGTH = 40
    }
}
