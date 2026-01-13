package io.writeopia.notemenu.ui.screen.configuration.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.notemenu.ui.screen.configuration.modifier.icon
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.sorting.OrderBy
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun SortOptions(
    showSortingOption: StateFlow<Boolean>,
    sortOptionState: StateFlow<OrderBy>,
    showSortOptionsRequest: () -> Unit,
    hideSortOptionsRequest: () -> Unit,
    selectSortOption: (OrderBy) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showSorting by showSortingOption.collectAsState()

    Box(modifier = modifier) {
        Icon(
            imageVector = WrIcons.sort,
            contentDescription = "Sort Options",
            modifier = Modifier.icon(showSortOptionsRequest).align(Alignment.Center).padding(2.dp),
            tint = MaterialTheme.colorScheme.onBackground,
        )

        DropdownMenu(
            expanded = showSorting,
            onDismissRequest = hideSortOptionsRequest,
            offset = DpOffset((-30).dp, (-6).dp)
        ) {
            val sortOption by sortOptionState.collectAsState()
            val iconTintColor = MaterialTheme.colorScheme.onBackground

            val background = @Composable { orderBy: OrderBy ->
                if (orderBy == sortOption) {
                    WriteopiaTheme.colorScheme.highlight
                } else {
                    Color.Unspecified
                }
            }

            DropdownMenuItem(
                modifier = Modifier.background(background(OrderBy.NAME)),
                leadingIcon = {
                    Icon(
                        imageVector = WrIcons.sortByName,
                        contentDescription = WrStrings.sortByName(),
                        tint = iconTintColor
                    )
                },
                onClick = {
                    selectSortOption(OrderBy.NAME)
                },
                text = {
                    Text(WrStrings.sortByName(), color = MaterialTheme.colorScheme.onBackground)
                }
            )

            DropdownMenuItem(
                modifier = Modifier.background(background(OrderBy.CREATE)),
                leadingIcon = {
                    Icon(
                        imageVector = WrIcons.sortByCreated,
                        contentDescription = WrStrings.sortByCreation(),
                        tint = iconTintColor
                    )
                },
                onClick = {
                    selectSortOption(OrderBy.CREATE)
                },
                text = {
                    Text(WrStrings.sortByCreation(), color = MaterialTheme.colorScheme.onBackground)
                }
            )

            DropdownMenuItem(
                modifier = Modifier.background(background(OrderBy.UPDATE)),
                leadingIcon = {
                    Icon(
                        imageVector = WrIcons.sortByUpdate,
                        contentDescription = WrStrings.sortByUpdate(),
                        tint = iconTintColor
                    )
                },
                onClick = {
                    selectSortOption(OrderBy.UPDATE)
                },
                text = {
                    Text(
                        WrStrings.sortByUpdate(),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            )
        }
    }
}
