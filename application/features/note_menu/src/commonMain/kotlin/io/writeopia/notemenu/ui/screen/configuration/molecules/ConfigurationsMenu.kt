package io.writeopia.notemenu.ui.screen.configuration.molecules

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.SlideInBox
import io.writeopia.commonui.options.slide.HorizontalOptions
import io.writeopia.notemenu.ui.screen.configuration.modifier.orderConfigModifierHorizontal
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.sorting.OrderBy
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val INNER_PADDING = 3

@Composable
internal fun BoxScope.MobileConfigurationsMenu(
    dialogMaxWidth: Dp = 500.dp,
    selected: Flow<Int>,
    visibilityState: Boolean,
    outsideClick: () -> Unit,
    staggeredGridOptionClick: () -> Unit,
    gridOptionClick: () -> Unit,
    listOptionClick: () -> Unit,
    sortingSelected: (OrderBy) -> Unit,
    sortingState: StateFlow<OrderBy>,
    modifier: Modifier = Modifier,
) {
    SlideInBox(
        modifier = modifier.align(Alignment.BottomCenter),
        editState = visibilityState,
        outsideClick = outsideClick,
        enterAnimationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = IntOffset.VisibilityThreshold
        ),
        animationLabel = "configurationsMenuAnimation"
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .widthIn(max = dialogMaxWidth)
                .clip(
                    MaterialTheme.shapes.large
                )
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ArrangementSection(selected, staggeredGridOptionClick, gridOptionClick, listOptionClick)

            SortingSection(sortingSelected = sortingSelected, sortingState)

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionText(text: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = 18.sp
        ),
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun ArrangementOptions(
    selected: Flow<Int>,
    staggeredGridOptionClick: () -> Unit,
    gridOptionClick: () -> Unit,
    listOptionClick: () -> Unit,
    height: Dp = 42.dp,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant

    HorizontalOptions(
        selectedState = selected,
        options = listOf<Pair<() -> Unit, @Composable RowScope.() -> Unit>>(
            staggeredGridOptionClick to {
                Icon(
                    modifier = Modifier
                        .orderConfigModifierHorizontal(clickable = gridOptionClick)
                        .weight(1F)
                        .zIndex(1000F),
                    imageVector = WrIcons.layoutStaggeredGrid,
                    contentDescription = "staggered card",
                    //            stringResource(R.string.staggered_card),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            gridOptionClick to {
                Icon(
                    modifier = Modifier
                        .orderConfigModifierHorizontal(clickable = gridOptionClick)
                        .weight(1F),
                    imageVector = WrIcons.layoutGrid,
                    contentDescription = "staggered card",
                    //            stringResource(R.string.staggered_card),
                    tint = tint
                )
            },
            listOptionClick to {
                Icon(
                    modifier = Modifier
                        .orderConfigModifierHorizontal(clickable = listOptionClick)
                        .weight(1F),
                    imageVector = WrIcons.layoutList,
                    contentDescription = "note list",
                    //            stringResource(R.string.note_list),
                    tint = tint
                )
            }
        ),
        modifier = Modifier.fillMaxWidth().padding(INNER_PADDING.dp),
        height = height
    )
}

@Composable
private fun ArrangementSection(
    selected: Flow<Int>,
    staggeredGridOptionClick: () -> Unit,
    gridOptionClick: () -> Unit,
    listOptionClick: () -> Unit
) {
    SectionText(text = WrStrings.arrangement())

    ArrangementOptions(
        selected = selected,
        staggeredGridOptionClick = staggeredGridOptionClick,
        gridOptionClick = gridOptionClick,
        listOptionClick = listOptionClick,
    )
}

@Composable
private fun SortingSection(sortingSelected: (OrderBy) -> Unit, sortingState: StateFlow<OrderBy>) {
    val order by sortingState.collectAsState()

    SectionText(text = WrStrings.sorting())
    val optionStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold
    )

    val background = @Composable { orderBy: OrderBy ->
        if (orderBy == order) {
            WriteopiaTheme.colorScheme.highlight
        } else {
            WriteopiaTheme.colorScheme.defaultButton
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = INNER_PADDING.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(WriteopiaTheme.colorScheme.defaultButton)
    ) {
        Text(
            modifier = Modifier
                .background(background(OrderBy.UPDATE))
                .clickable { sortingSelected(OrderBy.UPDATE) }
                .sortingOptionModifier(),
            text = WrStrings.lastUpdated(),
//            stringResource(R.string.last_updated)
            style = optionStyle,
        )

        HorizontalDivider(color = WriteopiaTheme.colorScheme.highlight)

        Text(
            modifier = Modifier
                .background(background(OrderBy.CREATE))
                .clickable { sortingSelected(OrderBy.CREATE) }
                .sortingOptionModifier(),
            text = WrStrings.created(),
//            stringResource(R.string.last_created),
            style = optionStyle,
        )

        HorizontalDivider(color = WriteopiaTheme.colorScheme.highlight)

        Text(
            modifier = Modifier
                .background(background(OrderBy.NAME))
                .clickable { sortingSelected(OrderBy.NAME) }
                .sortingOptionModifier(),
            text = WrStrings.sortByName(),
//            stringResource(R.string.name),
            style = optionStyle,
        )
    }
}

private fun Modifier.sortingOptionModifier(): Modifier = fillMaxWidth().padding(12.dp)

@Preview
@Composable
private fun ConfigurationsMenu_Preview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        MobileConfigurationsMenu(
            600.dp,
            MutableStateFlow(1),
            true,
            {},
            {},
            {},
            {},
            {},
            MutableStateFlow(OrderBy.NAME),
            Modifier
        )
    }
}

@Preview
@Composable
private fun ArrangementOptions_Preview() {
    ArrangementOptions(MutableStateFlow(1), {}, {}, {})
}
