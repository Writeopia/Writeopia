package io.writeopia.note_menu.data.usecase

import android.content.SharedPreferences
import io.writeopia.note_menu.viewmodel.NotesArrangement
import io.writeopia.sdk.persistence.core.extensions.toEntityField
import io.writeopia.sdk.persistence.core.sorting.OrderBy

/**
 * This class is responsible to keep the information of the preferences or the user about the
 * notes, like orderBy (creation, last edition, name...) and arrangement (cards, list...).
 */
internal class NotesConfigurationRepository(
    private val sharedPreferences: SharedPreferences
) {

    fun saveDocumentArrangementPref(arrangement: NotesArrangement) {
        sharedPreferences.edit()
            .run { putString(ARRANGE_PREFERENCE, arrangement.type) }
            .commit()
    }

    fun saveDocumentSortingPref(orderBy: OrderBy) {
        sharedPreferences.edit()
            .run { putString(ORDER_BY_PREFERENCE, orderBy.type.toEntityField()) }
            .commit()
    }

    fun arrangementPref(): String =
        sharedPreferences
            .getString(ARRANGE_PREFERENCE, NotesArrangement.GRID.type)
            ?: NotesArrangement.GRID.type

    fun getOrderPreference(): String? =
        sharedPreferences
            .getString(ORDER_BY_PREFERENCE, OrderBy.CREATE.type.toEntityField())


    companion object {
        private const val ORDER_BY_PREFERENCE = "order_by_preference"
        private const val ARRANGE_PREFERENCE = "arrange_preference"
    }
}