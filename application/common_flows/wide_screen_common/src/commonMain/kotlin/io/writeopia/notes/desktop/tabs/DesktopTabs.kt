package io.writeopia.notes.desktop.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavHostController
import io.writeopia.common.utils.NotesNavigation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** Where a tab is. Saved between launches, so it holds ids, never objects. */
@Serializable
sealed interface TabPlace {
    @Serializable
    @SerialName("root")
    data object Root : TabPlace

    @Serializable
    @SerialName("folder")
    data class Folder(val id: String) : TabPlace

    @Serializable
    @SerialName("document")
    data class Document(val id: String, val title: String) : TabPlace

    /** Graph, search, notifications...: not restored, the tab opens at Root instead. */
    @Serializable
    @SerialName("other")
    data object Other : TabPlace
}

/**
 * One tab: its own back stack (the [navController] of its NavHost) and its own view models.
 * The NavHost takes its view model store from the owner around it, so a tab that is the owner
 * keeps its screens apart from the other tabs, and closing it clears them.
 */
class DesktopTab(val id: String, initialPlace: TabPlace) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    /** Set by the tab's NavHost; null until the tab is shown the first time. */
    var navController: NavHostController? by mutableStateOf(null)

    /** Where the tab is now; written from the back stack, read for the title and the save. */
    var place: TabPlace by mutableStateOf(initialPlace)

    /** The route on screen, for the title of a tab whose place is [TabPlace.Other]. Not saved. */
    var route: String? by mutableStateOf(null)

    /** The place to go to the first time the tab is shown (a restored or "open in new tab" tab). */
    var pendingPlace: TabPlace? = initialPlace.takeIf { it != TabPlace.Root && it != TabPlace.Other }

    /** Something to do the first time the tab is shown that a place can't say (a new document). */
    var pendingNavigation: ((NavHostController) -> Unit)? = null

    /** The folder of the notes this tab showed last: the folder a new document goes to. */
    var notesNavigation: NotesNavigation = NotesNavigation.Root
}

@Serializable
data class SavedTabs(val places: List<TabPlace>, val activeIndex: Int) {
    fun toJson(): String = tabsJson.encodeToString(serializer(), this)

    companion object {
        fun fromJson(json: String): SavedTabs? =
            runCatching { tabsJson.decodeFromString(serializer(), json) }.getOrNull()
    }
}

private val tabsJson = Json { ignoreUnknownKeys = true }

/** Keeps the open tabs of each workspace between launches. */
interface TabsStore {
    fun load(workspaceId: String): SavedTabs?

    fun save(workspaceId: String, tabs: SavedTabs)
}

/**
 * The open tabs and which one is active. Never empty: closing the last tab leaves a new one at
 * Root. Plain state with no UI, so the rules are tested without Compose.
 */
class DesktopTabsState(
    initial: SavedTabs? = null,
    private val newId: () -> String = { Random.nextLong().toULong().toString(36) },
    private val onChange: (SavedTabs) -> Unit = {},
) {
    private val _tabs = MutableStateFlow(
        initial?.places?.takeIf { it.isNotEmpty() }?.map(::newTab) ?: listOf(newTab(TabPlace.Root))
    )
    val tabs: StateFlow<List<DesktopTab>> = _tabs.asStateFlow()

    private val _activeId = MutableStateFlow(
        _tabs.value[initial?.activeIndex?.coerceIn(0, _tabs.value.lastIndex) ?: 0].id
    )
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    val active: DesktopTab get() = _tabs.value.first { it.id == _activeId.value }

    fun open(place: TabPlace, activate: Boolean = true): DesktopTab {
        val tab = newTab(place)
        val index = _tabs.value.indexOfFirst { it.id == _activeId.value } + 1
        _tabs.value = _tabs.value.toMutableList().apply { add(index, tab) }
        if (activate) _activeId.value = tab.id
        changed()
        return tab
    }

    /** The tab showing this document, if any. */
    fun findDocument(id: String): DesktopTab? =
        _tabs.value.firstOrNull { (it.place as? TabPlace.Document)?.id == id }

    fun close(id: String) {
        val tabs = _tabs.value
        // The view models are cleared by the UI once the tab has left the screen.
        val index = tabs.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return

        val remaining = tabs - tabs[index]
        if (remaining.isEmpty()) {
            val fresh = newTab(TabPlace.Root)
            _tabs.value = listOf(fresh)
            _activeId.value = fresh.id
        } else {
            _tabs.value = remaining
            // Closing the active tab shows the one to its right, like a browser (or the new last).
            if (_activeId.value == id) {
                _activeId.value = remaining[index.coerceAtMost(remaining.lastIndex)].id
            }
        }
        changed()
    }

    fun closeActive() = close(_activeId.value)

    fun activate(id: String) {
        if (_tabs.value.none { it.id == id } || _activeId.value == id) return
        _activeId.value = id
        changed()
    }

    fun next() = step(1)

    fun previous() = step(-1)

    /** Tab [index] counting from 0; 8 (Cmd+9) is always the last one, like a browser. */
    fun select(index: Int) {
        val tabs = _tabs.value
        val target = if (index >= 8) tabs.lastIndex else index
        tabs.getOrNull(target)?.let { activate(it.id) }
    }

    fun move(from: Int, to: Int) {
        val tabs = _tabs.value.toMutableList()
        if (from !in tabs.indices || to !in tabs.indices || from == to) return
        tabs.add(to, tabs.removeAt(from))
        _tabs.value = tabs
        changed()
    }

    /** The active tab moved somewhere else in its own back stack. */
    fun placeChanged(tab: DesktopTab, place: TabPlace) {
        if (tab.place == place) return
        tab.place = place
        changed()
    }

    fun snapshot(): SavedTabs {
        val tabs = _tabs.value
        return SavedTabs(
            places = tabs.map { it.place },
            activeIndex = tabs.indexOfFirst { it.id == _activeId.value }.coerceAtLeast(0)
        )
    }

    /** Closes every tab, clearing their view models: the workspace is going away. */
    fun clear() {
        _tabs.value.forEach { it.viewModelStore.clear() }
    }

    private fun step(by: Int) {
        val tabs = _tabs.value
        if (tabs.size < 2) return
        val index = tabs.indexOfFirst { it.id == _activeId.value }
        activate(tabs[(index + by).mod(tabs.size)].id)
    }

    private fun newTab(place: TabPlace) = DesktopTab(newId(), place)

    private fun changed() = onChange(snapshot())
}
