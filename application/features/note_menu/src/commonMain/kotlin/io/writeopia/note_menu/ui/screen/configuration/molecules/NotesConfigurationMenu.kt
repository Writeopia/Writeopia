package io.writeopia.note_menu.ui.screen.configuration.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.writeopia.note_menu.ui.screen.configuration.atoms.ArrangementOptionsMenu
import io.writeopia.note_menu.ui.screen.configuration.atoms.SortOptions
import io.writeopia.sdk.persistence.core.sorting.OrderBy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun NotesConfigurationMenu(
    showSortingOption: StateFlow<Boolean>,
    selectedState: Flow<Int>,
    showSortOptionsRequest: () -> Unit,
    hideSortOptionsRequest: () -> Unit,
    selectSortOption: (OrderBy) -> Unit,
    staggeredGridSelected: () -> Unit,
    gridSelected: () -> Unit,
    listSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width = 36.dp

    Column(
        modifier = modifier.width(IntrinsicSize.Min),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ArrangementOptionsMenu(
            selectedState = selectedState,
            staggeredGridSelected = staggeredGridSelected,
            gridSelected = gridSelected,
            listSelected = listSelected,
            width = width,
            modifier = Modifier.background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        SortOptions(
            showSortingOption,
            showSortOptionsRequest,
            hideSortOptionsRequest,
            selectSortOption,
            modifier = Modifier.background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ).width(width)
        )
    }
}
