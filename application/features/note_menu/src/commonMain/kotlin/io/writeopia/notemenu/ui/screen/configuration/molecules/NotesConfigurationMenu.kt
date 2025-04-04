package io.writeopia.notemenu.ui.screen.configuration.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.writeopia.notemenu.ui.screen.configuration.atoms.ArrangementOptionsMenu
import io.writeopia.notemenu.ui.screen.configuration.atoms.SortOptions
import io.writeopia.sdk.models.sorting.OrderBy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun NotesConfigurationMenu(
    showSortingOption: StateFlow<Boolean>,
    selectedState: Flow<Int>,
    sortOptionState: StateFlow<OrderBy>,
    showSortOptionsRequest: () -> Unit,
    hideSortOptionsRequest: () -> Unit,
    selectSortOption: (OrderBy) -> Unit,
    staggeredGridSelected: () -> Unit,
    gridSelected: () -> Unit,
    listSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width = 40.dp
    val shape = RoundedCornerShape(8.dp)

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
                shape = shape
            ),
            selectorShape = shape
        )

        Spacer(modifier = Modifier.height(4.dp))

        SortOptions(
            showSortingOption,
            sortOptionState,
            showSortOptionsRequest,
            hideSortOptionsRequest,
            selectSortOption,
            modifier = Modifier.background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = shape
            ).size(width)
        )
    }
}
