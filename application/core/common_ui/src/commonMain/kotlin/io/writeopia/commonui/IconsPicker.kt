package io.writeopia.commonui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.colors.ColorUtils
import io.writeopia.common.utils.icons.WrIcons

@Composable
fun IconsPicker(
    modifier: Modifier = Modifier.size(200.dp),
    icons: Map<String, ImageVector> = WrIcons.allIcons,
    iconSize: Dp = 24.dp,
    initialIconName: String? = null,
    initialTint: Int? = null,
    colorSize: Dp = 12.dp,
    iconSelect: (String, Int) -> Unit,
) {
    val selectedBackground = MaterialTheme.colorScheme.primaryContainer
    val tintColors = ColorUtils.tintColors()

    // Without a saved tint the first color of the palette is selected, so the picker always
    // shows which color an icon gets: the theme's text color isn't in the palette.
    var tintColor by remember {
        mutableStateOf(initialTint?.let(::Color) ?: tintColors.first())
    }

    var selectedIconName by remember {
        mutableStateOf(initialIconName)
    }

    val colorShape = CircleShape

    Column {
        // Spread over the whole width when the colors fit, scrolls sideways when they don't:
        // fillMaxWidth sets the minimum width and the scroll only lifts the maximum. No
        // BoxWithConstraints here, it can't be measured by intrinsics inside a DropdownMenu.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tintColors.forEach { color ->
                val isSelected = color == tintColor
                val selectedSize = colorSize * 4 / 3

                // Same space for every color, so selecting one doesn't move the others.
                Box(
                    modifier = Modifier
                        .padding(horizontal = colorSize / 6)
                        .size(selectedSize),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) selectedSize else colorSize)
                            .background(color, colorShape)
                            .clip(colorShape)
                            .clickable {
                                tintColor = color
                                // Update the selection with new color if an icon is selected
                                selectedIconName?.let { iconName ->
                                    iconSelect(iconName, color.toArgb())
                                }
                            }
                    )
                }
            }
        }

        val spacing = Arrangement.spacedBy(8.dp)

        LazyVerticalGrid(
            modifier = modifier,
            columns = GridCells.Fixed(6),
            horizontalArrangement = spacing,
            verticalArrangement = spacing,
            contentPadding = PaddingValues(10.dp)
        ) {
            items(icons.toList()) { (name, vector) ->
                val isSelected = name == selectedIconName
                Icon(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (isSelected) selectedBackground else Color.Transparent,
                            MaterialTheme.shapes.medium
                        )
                        .clickable {
                            selectedIconName = name
                            iconSelect(name, tintColor.toArgb())
                        }
                        .padding(4.dp)
                        .size(iconSize),
                    imageVector = vector,
                    contentDescription = name,
                    tint = tintColor
                )
            }
        }
    }
}
