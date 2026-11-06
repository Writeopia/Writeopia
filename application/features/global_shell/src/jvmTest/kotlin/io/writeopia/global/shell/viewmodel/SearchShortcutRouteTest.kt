package io.writeopia.global.shell.viewmodel

import io.writeopia.common.utils.Destinations
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The search shortcut opens the global search on the documents menu only. */
class SearchShortcutRouteTest {

    @Test
    fun `the documents menu routes take the shortcut`() {
        assertTrue(GlobalShellKmpViewModel.isDocumentsMenuRoute("${Destinations.CHOOSE_NOTE.id}/{navigationType}/{path}"))
        assertTrue(GlobalShellKmpViewModel.isDocumentsMenuRoute("${Destinations.CHOOSE_NOTE.id}/root/path"))
    }

    @Test
    fun `the editor, the settings and an unknown screen leave it alone`() {
        assertFalse(GlobalShellKmpViewModel.isDocumentsMenuRoute("${Destinations.EDITOR.id}/{noteId}/{noteTitle}"))
        assertFalse(GlobalShellKmpViewModel.isDocumentsMenuRoute(Destinations.SETTINGS_ACCOUNT.id))
        assertFalse(GlobalShellKmpViewModel.isDocumentsMenuRoute(Destinations.FORCE_GRAPH.id))
        assertFalse(GlobalShellKmpViewModel.isDocumentsMenuRoute(null))
    }
}
