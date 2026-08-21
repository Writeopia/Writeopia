package io.writeopia.ui.drawer.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.icons.WrSdkIcons
import io.writeopia.ui.model.DrawInfo

class SpreadsheetDrawer(
    private val onCellTextChange: (spreadsheetId: String, rowIndex: Int, cellIndex: Int, newText: String) -> Unit,
    private val onAddRow: (spreadsheetId: String) -> Unit,
    private val onAddColumn: (spreadsheetId: String) -> Unit,
    private val onCellAction: (spreadsheetId: String, rowIndex: Int, cellIndex: Int) -> Unit = { _, _, _ -> },
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val scrollState = rememberScrollState()
        val rows = step.steps

        // Interaction source for the whole spreadsheet to detect hover
        val spreadsheetInteractionSource = remember { MutableInteractionSource() }
        val isSpreadsheetHovered by spreadsheetInteractionSource.collectIsHoveredAsState()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .hoverable(spreadsheetInteractionSource)
                .height(IntrinsicSize.Min)
        ) {
            // Left side: spreadsheet content + add row button (column layout)
            Column(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Scrollable spreadsheet content
                Box(
                    modifier = Modifier.horizontalScroll(scrollState)
                ) {
                    Column(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        rows.forEachIndexed { rowIndex, row ->
                            val isHeader = rowIndex == 0

                            Row(
                                modifier = Modifier.height(IntrinsicSize.Min)
                            ) {
                                row.steps.forEachIndexed { cellIndex, cell ->
                                    SpreadsheetCell(
                                        text = cell.text ?: "",
                                        isHeader = isHeader,
                                        onTextChange = { newText ->
                                            onCellTextChange(step.id, rowIndex, cellIndex, newText)
                                        },
                                        onActionClick = {
                                            onCellAction(step.id, rowIndex, cellIndex)
                                        },
                                        showBorderEnd = cellIndex < row.steps.size - 1,
                                        showBorderBottom = rowIndex < rows.size - 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Add row button at the bottom (matches spreadsheet width)
                if (isSpreadsheetHovered) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onAddRow(step.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = WrSdkIcons.plus,
                            contentDescription = "Add row",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Add column button on the right (outside scroll, next to spreadsheet)
            if (isSpreadsheetHovered) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(20.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onAddColumn(step.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = WrSdkIcons.plus,
                        contentDescription = "Add column",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpreadsheetCell(
    text: String,
    isHeader: Boolean,
    onTextChange: (String) -> Unit,
    onActionClick: () -> Unit,
    showBorderEnd: Boolean,
    showBorderBottom: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var localText by remember(text) { mutableStateOf(text) }

    val backgroundColor = if (isHeader) {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
    } else {
        MaterialTheme.colorScheme.background
    }

    Box(
        modifier = modifier
            .hoverable(interactionSource)
            .defaultMinSize(minWidth = 120.dp, minHeight = 40.dp)
            .background(backgroundColor)
            .then(
                if (showBorderEnd) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                    ).padding(end = 1.dp)
                } else {
                    Modifier
                }
            )
            .then(
                if (showBorderBottom) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                    ).padding(bottom = 1.dp)
                } else {
                    Modifier
                }
            )
            .padding(8.dp)
    ) {
        BasicTextField(
            value = localText,
            onValueChange = { newValue ->
                localText = newValue
                onTextChange(newValue)
            },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .defaultMinSize(minWidth = 104.dp),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true
        )

        // Action button on hover
        if (isHovered) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                    .clickable { onActionClick() }
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = WrSdkIcons.moreVert,
                    contentDescription = "Cell actions",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}
