package io.writeopia.ui.drawer.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.model.draganddrop.DropInfo
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.ui.components.SwipeBox
import io.writeopia.ui.components.multiselection.SelectableByDrag
import io.writeopia.ui.draganddrop.target.DragRowTarget
import io.writeopia.ui.draganddrop.target.DropTargetVerticalDivision
import io.writeopia.ui.draganddrop.target.InBounds
import io.writeopia.ui.draganddrop.target.external.externalImageDropTarget
import io.writeopia.ui.draganddrop.target.external.shouldAcceptImageDrop
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.icons.WrSdkIcons
import io.writeopia.ui.model.DrawConfig
import io.writeopia.ui.model.DrawInfo

private const val DEFAULT_COLUMN_WIDTH = 120
private const val MIN_COLUMN_WIDTH = 50
private const val MAX_COLUMN_WIDTH = 500

class SpreadsheetDrawer(
    private val dragIconWidth: Dp = 24.dp,
    private val config: DrawConfig,
    private val onSelected: (Boolean, Double) -> Unit = { _, _ -> },
    private val onDragHover: (Double) -> Unit = {},
    private val onDragStart: () -> Unit = {},
    private val onDragStop: () -> Unit = {},
    private val moveRequest: (Action.Move) -> Unit = {},
    private val enabled: Boolean = true,
    private val receiveExternalFile: (List<ExternalFile>, Double) -> Unit = { _, _ -> },
    private val onCellTextChange: (spreadsheetId: String, rowIndex: Int, cellIndex: Int, newText: String) -> Unit,
    private val onAddRow: (spreadsheetId: String) -> Unit,
    private val onAddColumn: (spreadsheetId: String) -> Unit,
    private val onColumnWidthChange: (spreadsheetId: String, columnIndex: Int, newWidth: Int) -> Unit = { _, _, _ -> },
    private val onColumnResizeStart: () -> Unit = {},
    private val onColumnResizeEnd: () -> Unit = {},
    private val onRowAction: (spreadsheetId: String, rowIndex: Int) -> Unit = { _, _ -> },
    private val onColumnAction: (spreadsheetId: String, columnIndex: Int) -> Unit = { _, _ -> },
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val scrollState = rememberScrollState()
        val rows = step.steps
        val dropInfo = DropInfo(step, drawInfo.position)
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()
        val density = LocalDensity.current

        // Parse column widths from spreadsheet metadata (JSON format: {"columnWidths":[120,150,100]})
        val columnCount = rows.firstOrNull()?.steps?.size ?: 0
        val persistedColumnWidths = remember(step.text, columnCount) {
            parseColumnWidths(step.text, columnCount)
        }

        // Local state for smooth dragging - updated in real-time, persisted on drag end
        var localColumnWidths by remember(persistedColumnWidths) {
            mutableStateOf(persistedColumnWidths)
        }

        // Column resize drag state - lifted up so all cells in the column can use it
        // columnDragOffset is in Dp (converted from pixels using density)
        var resizingColumnIndex by remember { mutableStateOf(-1) }
        var columnDragOffset by remember { mutableStateOf(0f) }

        // Track which row/column is currently hovered for action buttons
        var hoveredRowIndex by remember { mutableStateOf(-1) }
        var hoveredColumnIndex by remember { mutableStateOf(-1) }

        // Interaction source for the last row
        val lastRowInteractionSource = remember { MutableInteractionSource() }
        val isLastRowHovered by lastRowInteractionSource.collectIsHoveredAsState()

        // Interaction source for the add row button area
        val addRowAreaInteractionSource = remember { MutableInteractionSource() }
        val isAddRowAreaHovered by addRowAreaInteractionSource.collectIsHoveredAsState()

        // Calculate total spreadsheet content width (cells + borders between cells)
        val totalSpreadsheetWidth = remember(localColumnWidths) {
            localColumnWidths.sum() +
                maxOf(0, (localColumnWidths.size - 1) * 4) // 4dp border/resizer between columns
        }

        // Interaction source for the last column
        val lastColumnInteractionSource = remember { MutableInteractionSource() }
        val isLastColumnHovered by lastColumnInteractionSource.collectIsHoveredAsState()

        // Interaction source for the add column button area
        val addColumnAreaInteractionSource = remember { MutableInteractionSource() }
        val isAddColumnAreaHovered by addColumnAreaInteractionSource.collectIsHoveredAsState()

        // Target alpha: 100% when hovering on button area, 50% when hovering on last row/column only
        val targetAddRowButtonAlpha = when {
            isAddRowAreaHovered -> 1f
            isLastRowHovered -> 0.5f
            else -> 0f
        }
        val targetAddColumnButtonAlpha = when {
            isAddColumnAreaHovered -> 1f
            isLastColumnHovered -> 0.5f
            else -> 0f
        }

        // Animate the alpha values
        val addRowButtonAlpha by animateFloatAsState(
            targetValue = targetAddRowButtonAlpha,
            animationSpec = tween(durationMillis = 150)
        )
        val addColumnButtonAlpha by animateFloatAsState(
            targetValue = targetAddColumnButtonAlpha,
            animationSpec = tween(durationMillis = 150)
        )

        SelectableByDrag(
            modifier = Modifier.dragAndDropTarget(
                shouldStartDragAndDrop = ::shouldAcceptImageDrop,
                target = externalImageDropTarget(
                    onStart = onDragStart,
                    onEnd = onDragStop,
                    onEnter = {
                        onDragHover(drawInfo.position)
                    },
                    onExit = {},
                    onFileReceived = { files ->
                        receiveExternalFile(files, drawInfo.position + 1)
                    }
                )
            )
        ) { isInsideDrag ->
            if (isInsideDrag != null) {
                LaunchedEffect(isInsideDrag) {
                    onSelected(isInsideDrag, drawInfo.position)
                }
            }

            DropTargetVerticalDivision(
                modifier = Modifier
            ) { inBound, data ->
                when (inBound) {
                    InBounds.OUTSIDE -> {}
                    InBounds.INSIDE_UP -> {
                        handleDrag(drawInfo.previousPosition ?: drawInfo.position, data)
                    }
                    InBounds.INSIDE_DOWN -> {
                        handleDrag(drawInfo.position, data)
                    }
                }

                SwipeBox(
                    modifier = Modifier.hoverable(interactionSource),
                    defaultColor = Color.Transparent,
                    activeColor = config.selectedColor(),
                    activeBorderColor = config.selectedBorderColor(),
                    isOnEditState = drawInfo.selectMode,
                    swipeListener = { isSelected ->
                        onSelected(isSelected, drawInfo.position)
                    },
                    paddingValues = PaddingValues(0.dp)
                ) {
                    DragRowTarget(
                        modifier = Modifier.fillMaxWidth(),
                        dataToDrop = dropInfo,
                        showIcon = isHovered && enabled,
                        position = drawInfo.position,
                        dragIconWidth = dragIconWidth,
                        onDragStart = onDragStart,
                        onDragStop = onDragStop,
                        isHoldDraggable = drawInfo.selectMode,
                        emptySpaceClick = {},
                        onClick = {
                            onSelected(!drawInfo.selectMode, drawInfo.position)
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            // Row containing spreadsheet + add column button
                            Row(
                                modifier = Modifier.height(IntrinsicSize.Min)
                            ) {
                                // Scrollable spreadsheet content
                                Box(
                                    modifier = Modifier.horizontalScroll(scrollState)
                                ) {
                                    Row {
                                        // Row action buttons column (on the left)
                                        Column(
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            // Empty space for alignment with column action buttons
                                            Spacer(modifier = Modifier.height(24.dp))

                                            rows.forEachIndexed { rowIndex, _ ->
                                                val rowActionInteractionSource = remember { MutableInteractionSource() }
                                                val isRowActionHovered by rowActionInteractionSource.collectIsHoveredAsState()

                                                val targetAlpha = when {
                                                    isRowActionHovered -> 1f
                                                    hoveredRowIndex == rowIndex -> 1f
                                                    else -> 0f
                                                }
                                                val rowActionAlpha by animateFloatAsState(
                                                    targetValue = targetAlpha,
                                                    animationSpec = tween(durationMillis = 150)
                                                )

                                                Box(
                                                    modifier = Modifier
                                                        .width(20.dp)
                                                        .defaultMinSize(minHeight = 40.dp)
                                                        .height(IntrinsicSize.Min)
                                                        .hoverable(rowActionInteractionSource),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (rowActionAlpha > 0f) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(
                                                                    MaterialTheme.colorScheme.onBackground.copy(
                                                                        alpha = 0.1f * rowActionAlpha
                                                                    )
                                                                )
                                                                .clickable { onRowAction(step.id, rowIndex) }
                                                                .padding(2.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = WrSdkIcons.moreVert,
                                                                contentDescription = "Row actions",
                                                                modifier = Modifier.size(14.dp),
                                                                tint = MaterialTheme.colorScheme.onBackground.copy(
                                                                    alpha = 0.6f * rowActionAlpha
                                                                )
                                                            )
                                                        }
                                                    }
                                                }

                                                // Add spacing for border between rows
                                                if (rowIndex < rows.size - 1) {
                                                    Spacer(modifier = Modifier.height(1.dp))
                                                }
                                            }
                                        }

                                        // Main spreadsheet grid
                                        Column {
                                            // Column action buttons row (on top)
                                            Row(
                                                modifier = Modifier
                                                    .height(24.dp)
                                                    .padding(bottom = 4.dp)
                                            ) {
                                                rows.firstOrNull()?.steps?.forEachIndexed { cellIndex, _ ->
                                                    val cellWidth = localColumnWidths.getOrElse(cellIndex) { DEFAULT_COLUMN_WIDTH }
                                                    val effectiveWidth = if (resizingColumnIndex == cellIndex) {
                                                        (cellWidth + columnDragOffset).coerceIn(
                                                            MIN_COLUMN_WIDTH.toFloat(),
                                                            MAX_COLUMN_WIDTH.toFloat()
                                                        )
                                                    } else {
                                                        cellWidth.toFloat()
                                                    }
                                                    val isLastColumn = cellIndex == (rows.firstOrNull()?.steps?.size ?: 1) - 1

                                                    val columnActionInteractionSource = remember { MutableInteractionSource() }
                                                    val isColumnActionHovered by columnActionInteractionSource.collectIsHoveredAsState()

                                                    val targetAlpha = when {
                                                        isColumnActionHovered -> 1f
                                                        hoveredColumnIndex == cellIndex -> 1f
                                                        else -> 0f
                                                    }
                                                    val columnActionAlpha by animateFloatAsState(
                                                        targetValue = targetAlpha,
                                                        animationSpec = tween(durationMillis = 150)
                                                    )

                                                    Box(
                                                        modifier = Modifier
                                                            .width(effectiveWidth.dp)
                                                            .fillMaxHeight()
                                                            .hoverable(columnActionInteractionSource),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (columnActionAlpha > 0f) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(20.dp)
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(
                                                                        MaterialTheme.colorScheme.onBackground.copy(
                                                                            alpha = 0.1f * columnActionAlpha
                                                                        )
                                                                    )
                                                                    .clickable { onColumnAction(step.id, cellIndex) }
                                                                    .padding(2.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = WrSdkIcons.moreHoriz,
                                                                    contentDescription = "Column actions",
                                                                    modifier = Modifier.size(14.dp),
                                                                    tint = MaterialTheme.colorScheme.onBackground.copy(
                                                                        alpha = 0.6f * columnActionAlpha
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // Add spacing for resizer between columns
                                                    if (!isLastColumn) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                    }
                                                }
                                            }

                                            // Spreadsheet cells
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
                                                    val isLastRow = rowIndex == rows.size - 1

                                                    Row(
                                                        modifier = Modifier.height(IntrinsicSize.Min)
                                                    ) {
                                                        row.steps.forEachIndexed { cellIndex, cell ->
                                                            val isLastColumn = cellIndex == row.steps.size - 1
                                                            val cellWidth = localColumnWidths.getOrElse(cellIndex) { DEFAULT_COLUMN_WIDTH }

                                                            // Calculate effective width including drag offset if this column is being resized
                                                            val effectiveWidth = if (resizingColumnIndex == cellIndex) {
                                                                (cellWidth + columnDragOffset).coerceIn(
                                                                    MIN_COLUMN_WIDTH.toFloat(),
                                                                    MAX_COLUMN_WIDTH.toFloat()
                                                                )
                                                            } else {
                                                                cellWidth.toFloat()
                                                            }

                                                            SpreadsheetCell(
                                                                text = cell.text ?: "",
                                                                isHeader = isHeader,
                                                                width = effectiveWidth.dp,
                                                                onTextChange = { newText ->
                                                                    onCellTextChange(step.id, rowIndex, cellIndex, newText)
                                                                },
                                                                onHoverChanged = { cellHovered ->
                                                                    if (cellHovered) {
                                                                        hoveredRowIndex = rowIndex
                                                                        hoveredColumnIndex = cellIndex
                                                                    } else if (hoveredRowIndex == rowIndex &&
                                                                        hoveredColumnIndex == cellIndex
                                                                    ) {
                                                                        hoveredRowIndex = -1
                                                                        hoveredColumnIndex = -1
                                                                    }
                                                                },
                                                                showBorderEnd = !isLastColumn,
                                                                showBorderBottom = rowIndex < rows.size - 1,
                                                                onDragStart = {
                                                                    resizingColumnIndex = cellIndex
                                                                    columnDragOffset = 0f
                                                                    onColumnResizeStart()
                                                                },
                                                                onDrag = { deltaPx ->
                                                                    // Convert pixel delta to Dp
                                                                    columnDragOffset += with(density) { deltaPx.toDp().value }
                                                                },
                                                                onDragEnd = {
                                                                    val finalWidth = (cellWidth + columnDragOffset)
                                                                        .toInt()
                                                                        .coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
                                                                    resizingColumnIndex = -1
                                                                    columnDragOffset = 0f
                                                                    onColumnResizeEnd()
                                                                    onColumnWidthChange(step.id, cellIndex, finalWidth)
                                                                },
                                                                extraModifier = when {
                                                                    isLastRow && isLastColumn ->
                                                                        Modifier
                                                                            .hoverable(lastRowInteractionSource)
                                                                            .hoverable(lastColumnInteractionSource)
                                                                    isLastRow -> Modifier.hoverable(lastRowInteractionSource)
                                                                    isLastColumn -> Modifier.hoverable(lastColumnInteractionSource)
                                                                    else -> Modifier
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Add column button area (includes spacer for hover detection)
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .hoverable(addColumnAreaInteractionSource)
                                ) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(20.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f * addColumnButtonAlpha),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f * addColumnButtonAlpha),
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
                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f * addColumnButtonAlpha)
                                        )
                                    }
                                }
                            }

                            // Add row button area (includes spacer for hover detection)
                            Column(
                                modifier = Modifier
                                    .width(totalSpreadsheetWidth.dp)
                                    .hoverable(addRowAreaInteractionSource)
                            ) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(20.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f * addRowButtonAlpha),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f * addRowButtonAlpha),
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
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f * addRowButtonAlpha)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleDrag(position: Double, data: DropInfo?) {
        onDragHover(position)

        if (data != null) {
            moveRequest(
                Action.Move(
                    data.info as StoryStep,
                    positionFrom = data.positionFrom,
                    positionTo = position
                )
            )
        }
    }
}

@Composable
private fun SpreadsheetCell(
    text: String,
    isHeader: Boolean,
    width: Dp,
    onTextChange: (String) -> Unit,
    onHoverChanged: (Boolean) -> Unit,
    showBorderEnd: Boolean,
    showBorderBottom: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    extraModifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var localText by remember(text) { mutableStateOf(text) }

    LaunchedEffect(isHovered) {
        onHoverChanged(isHovered)
    }

    val backgroundColor = if (isHeader) {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
    } else {
        MaterialTheme.colorScheme.background
    }

    val borderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)

    Row(
        modifier = modifier
            .hoverable(interactionSource)
            .then(extraModifier)
            .fillMaxHeight()
    ) {
        // Cell content
        Box(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                .defaultMinSize(minHeight = 40.dp)
                .background(backgroundColor)
                .then(
                    if (showBorderBottom) {
                        Modifier.border(
                            width = 1.dp,
                            color = borderColor
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
                    .align(Alignment.TopStart)
                    .fillMaxWidth(),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = false
            )
        }

        // Draggable border/resizer
        if (showBorderEnd) {
            var isDragging by remember { mutableStateOf(false) }
            val resizerInteractionSource = remember { MutableInteractionSource() }
            val isResizerHovered by resizerInteractionSource.collectIsHoveredAsState()

            val draggableState = rememberDraggableState { delta ->
                onDrag(delta)
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .hoverable(resizerInteractionSource)
                    .pointerHoverIcon(PointerIcon.Crosshair)
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Horizontal,
                        onDragStarted = {
                            isDragging = true
                            onDragStart()
                        },
                        onDragStopped = {
                            isDragging = false
                            onDragEnd()
                        }
                    )
                    .background(
                        if (isDragging || isResizerHovered) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        } else {
                            borderColor
                        }
                    )
            )
        }
    }
}

/**
 * Parses column widths from the spreadsheet metadata JSON.
 * Returns a list of widths with the specified column count, using defaults for missing values.
 * Format: {"columnWidths":[120,150,100]}
 */
private fun parseColumnWidths(text: String?, columnCount: Int): List<Int> {
    if (text.isNullOrBlank() || columnCount == 0) {
        return List(columnCount) { DEFAULT_COLUMN_WIDTH }
    }

    return try {
        // Simple regex to extract the columnWidths array
        val regex = """"columnWidths"\s*:\s*\[([^\]]*)]""".toRegex()
        val match = regex.find(text)
        val arrayContent = match?.groupValues?.getOrNull(1) ?: ""

        val widths = if (arrayContent.isBlank()) {
            emptyList()
        } else {
            arrayContent.split(",").mapNotNull { it.trim().toIntOrNull() }
        }

        // Ensure we have the right number of columns, using defaults for missing
        (0 until columnCount).map { index ->
            widths.getOrElse(index) { DEFAULT_COLUMN_WIDTH }
        }
    } catch (e: Exception) {
        List(columnCount) { DEFAULT_COLUMN_WIDTH }
    }
}
