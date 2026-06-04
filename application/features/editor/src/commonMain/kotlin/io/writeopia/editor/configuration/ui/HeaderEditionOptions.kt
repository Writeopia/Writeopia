package io.writeopia.editor.configuration.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.theme.WriteopiaTheme

@Composable
fun HeaderEditionOptions(
    availableColors: List<Int>,
    selectedColor: Int?,
    onColorSelection: (Int?) -> Unit,
) {
    val colors = listOf(Color.Transparent.toArgb()) + availableColors

    Text(
        modifier = Modifier.padding(horizontal = 16.dp),
        text = "Header",
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(12.dp))

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(colors) { i, color ->
            val isSelected = if (i == 0) selectedColor == null else color == selectedColor

            val cornerRadius by animateDpAsState(
                targetValue = if (isSelected) 14.dp else 30.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "cornerRadius"
            )

            val boxWidth by animateDpAsState(
                targetValue = if (isSelected) 50.dp else 40.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "boxWidth"
            )

            val shape = RoundedCornerShape(cornerRadius)

            Box(
                modifier = Modifier
                    .clip(shape)
                    .clickable {
                        onColorSelection(if (i == 0) null else color)
                    }
                    .border(
                        BorderStroke(1.dp, WriteopiaTheme.colorScheme.dividerColor),
                        shape = shape
                    )
                    .height(40.dp)
                    .width(boxWidth)
                    .background(Color(color))
            ) {
                if (i == 0) {
                    Icon(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(26.dp),
                        imageVector = WrIcons.transparent,
                        contentDescription = "Remove color",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}
