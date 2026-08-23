package io.writeopia.sdk.manager

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.utils.collections.toSortedMutableMap
import io.writeopia.sdk.utils.iterables.addElementInPosition

/**
 * Class dedicated to handle spreadsheet-related operations on StorySteps.
 */
class SpreadsheetHandler {

    /**
     * Creates a spreadsheet with the specified number of columns and rows.
     *
     * @param currentStory The current story map
     * @param position The position to add the spreadsheet
     * @param columnCount The number of columns in the spreadsheet
     * @param rowCount The number of rows in the spreadsheet
     * @param insertMode If true, inserts the spreadsheet without replacing existing content
     * @return The updated story map with the new spreadsheet
     */
    fun createSpreadsheet(
        currentStory: Map<Double, StoryStep>,
        position: Double,
        columnCount: Int,
        rowCount: Int = 3,
        insertMode: Boolean = false
    ): Map<Double, StoryStep> {
        // Create rows with cells
        val rows = (0 until rowCount).map {
            val cells = (0 until columnCount).map {
                StoryStep(
                    type = StoryTypes.SPREADSHEET_CELL.type,
                    text = ""
                )
            }
            StoryStep(
                type = StoryTypes.SPREADSHEET_ROW.type,
                steps = cells
            )
        }

        // Create the spreadsheet with the rows
        val spreadsheet = StoryStep(
            type = StoryTypes.SPREADSHEET.type,
            steps = rows
        )

        return if (insertMode) {
            currentStory.addElementInPosition(spreadsheet, position)
        } else {
            val mutable = currentStory.toSortedMutableMap()
            mutable[position] = spreadsheet
            mutable
        }
    }

    /**
     * Updates the text of a specific cell within a spreadsheet.
     *
     * @param currentStory The current story map
     * @param spreadsheetId The ID of the spreadsheet StoryStep
     * @param rowIndex The index of the row (0-based)
     * @param cellIndex The index of the cell within the row (0-based)
     * @param newText The new text for the cell
     * @return The updated story map, or null if the spreadsheet wasn't found
     */
    fun updateSpreadsheetCell(
        currentStory: Map<Double, StoryStep>,
        spreadsheetId: String,
        rowIndex: Int,
        cellIndex: Int,
        newText: String
    ): Map<Double, StoryStep>? {
        val spreadsheetEntry = currentStory.entries.find { it.value.id == spreadsheetId }
            ?: return null
        val position = spreadsheetEntry.key
        val spreadsheet = spreadsheetEntry.value

        val rows = spreadsheet.steps.toMutableList()
        if (rowIndex !in rows.indices) return null

        val row = rows[rowIndex]
        val cells = row.steps.toMutableList()
        if (cellIndex !in cells.indices) return null

        // Update the cell text
        cells[cellIndex] = cells[cellIndex].copy(text = newText)

        // Update the row with new cells
        rows[rowIndex] = row.copy(steps = cells)

        // Update the spreadsheet with new rows
        val updatedSpreadsheet = spreadsheet.copy(steps = rows)

        val mutable = currentStory.toSortedMutableMap()
        mutable[position] = updatedSpreadsheet
        return mutable
    }

    /**
     * Adds a new row to a spreadsheet with the same number of columns as existing rows.
     *
     * @param currentStory The current story map
     * @param spreadsheetId The ID of the spreadsheet StoryStep
     * @return The updated story map, or null if the spreadsheet wasn't found
     */
    fun addSpreadsheetRow(
        currentStory: Map<Double, StoryStep>,
        spreadsheetId: String
    ): Map<Double, StoryStep>? {
        val spreadsheetEntry = currentStory.entries.find { it.value.id == spreadsheetId }
            ?: return null
        val position = spreadsheetEntry.key
        val spreadsheet = spreadsheetEntry.value

        val existingRows = spreadsheet.steps
        if (existingRows.isEmpty()) return null

        // Get column count from first row
        val columnCount = existingRows.first().steps.size

        // Create new cells
        val newCells = (0 until columnCount).map {
            StoryStep(
                type = StoryTypes.SPREADSHEET_CELL.type,
                text = ""
            )
        }

        // Create new row
        val newRow = StoryStep(
            type = StoryTypes.SPREADSHEET_ROW.type,
            steps = newCells
        )

        // Add the new row to the spreadsheet
        val updatedSpreadsheet = spreadsheet.copy(steps = existingRows + newRow)

        val mutable = currentStory.toSortedMutableMap()
        mutable[position] = updatedSpreadsheet
        return mutable
    }

    /**
     * Adds a new column to a spreadsheet by adding a cell to each row.
     *
     * @param currentStory The current story map
     * @param spreadsheetId The ID of the spreadsheet StoryStep
     * @return The updated story map, or null if the spreadsheet wasn't found
     */
    fun addSpreadsheetColumn(
        currentStory: Map<Double, StoryStep>,
        spreadsheetId: String
    ): Map<Double, StoryStep>? {
        val spreadsheetEntry = currentStory.entries.find { it.value.id == spreadsheetId }
            ?: return null
        val position = spreadsheetEntry.key
        val spreadsheet = spreadsheetEntry.value

        val existingRows = spreadsheet.steps
        if (existingRows.isEmpty()) return null

        // Add a new cell to each row
        val updatedRows = existingRows.map { row ->
            val newCell = StoryStep(
                type = StoryTypes.SPREADSHEET_CELL.type,
                text = ""
            )
            row.copy(steps = row.steps + newCell)
        }

        val updatedSpreadsheet = spreadsheet.copy(steps = updatedRows)

        val mutable = currentStory.toSortedMutableMap()
        mutable[position] = updatedSpreadsheet
        return mutable
    }

    /**
     * Updates the width of a specific column in a spreadsheet.
     * Column widths are stored as JSON metadata in the spreadsheet's text field.
     * Format: {"columnWidths":[120,150,100]}
     *
     * @param currentStory The current story map
     * @param spreadsheetId The ID of the spreadsheet StoryStep
     * @param columnIndex The index of the column to update (0-based)
     * @param newWidth The new width for the column in dp
     * @return The updated story map, or null if the spreadsheet wasn't found
     */
    fun updateSpreadsheetColumnWidth(
        currentStory: Map<Double, StoryStep>,
        spreadsheetId: String,
        columnIndex: Int,
        newWidth: Int
    ): Map<Double, StoryStep>? {
        val spreadsheetEntry = currentStory.entries.find { it.value.id == spreadsheetId }
            ?: return null
        val position = spreadsheetEntry.key
        val spreadsheet = spreadsheetEntry.value

        // Get the column count from the first row
        val columnCount = spreadsheet.steps.firstOrNull()?.steps?.size ?: return null
        if (columnIndex !in 0 until columnCount) return null

        // Parse existing column widths from JSON or use defaults
        val existingWidths = parseColumnWidths(spreadsheet.text, columnCount)

        // Update the column width at the specified index
        val updatedWidths = existingWidths.toMutableList().apply {
            this[columnIndex] = newWidth
        }

        // Serialize back to JSON
        val jsonText = """{"columnWidths":[${updatedWidths.joinToString(",")}]}"""
        val updatedSpreadsheet = spreadsheet.copy(text = jsonText)

        val mutable = currentStory.toSortedMutableMap()
        mutable[position] = updatedSpreadsheet
        return mutable
    }

    fun deleteSpreadsheetRow(
        currentStory: Map<Double, StoryStep>,
        spreadsheetId: String,
        rowIndex: Int
    ): Map<Double, StoryStep>? {
        val mutable = currentStory.toSortedMutableMap()
        val (position, spreadsheet) = mutable.entries.find { it.value.id == spreadsheetId }
            ?: return null

        val rows = spreadsheet.steps.toMutableList()
        if (rowIndex < 0 || rowIndex >= rows.size || rows.size <= 1) return null

        rows.removeAt(rowIndex)
        val updatedSpreadsheet = spreadsheet.copy(steps = rows)
        mutable[position] = updatedSpreadsheet
        return mutable
    }

    fun deleteSpreadsheetColumn(
        currentStory: Map<Double, StoryStep>,
        spreadsheetId: String,
        columnIndex: Int
    ): Map<Double, StoryStep>? {
        val mutable = currentStory.toSortedMutableMap()
        val (position, spreadsheet) = mutable.entries.find { it.value.id == spreadsheetId }
            ?: return null

        val rows = spreadsheet.steps
        if (rows.isEmpty()) return null

        val columnCount = rows.firstOrNull()?.steps?.size ?: return null
        if (columnIndex < 0 || columnIndex >= columnCount || columnCount <= 1) return null

        val updatedRows = rows.map { row ->
            val cells = row.steps.toMutableList()
            cells.removeAt(columnIndex)
            row.copy(steps = cells)
        }

        val updatedSpreadsheet = spreadsheet.copy(steps = updatedRows)
        mutable[position] = updatedSpreadsheet
        return mutable
    }

    fun addSpreadsheetRowAt(
        currentStory: Map<Double, StoryStep>,
        spreadsheetId: String,
        rowIndex: Int
    ): Map<Double, StoryStep>? {
        val mutable = currentStory.toSortedMutableMap()
        val (position, spreadsheet) = mutable.entries.find { it.value.id == spreadsheetId }
            ?: return null

        val rows = spreadsheet.steps.toMutableList()
        val columnCount = rows.firstOrNull()?.steps?.size ?: return null

        val newCells = (0 until columnCount).map {
            StoryStep(type = StoryTypes.SPREADSHEET_CELL.type, text = "")
        }
        val newRow = StoryStep(type = StoryTypes.SPREADSHEET_ROW.type, steps = newCells)

        val insertIndex = rowIndex.coerceIn(0, rows.size)
        rows.add(insertIndex, newRow)

        val updatedSpreadsheet = spreadsheet.copy(steps = rows)
        mutable[position] = updatedSpreadsheet
        return mutable
    }

    fun addSpreadsheetColumnAt(
        currentStory: Map<Double, StoryStep>,
        spreadsheetId: String,
        columnIndex: Int
    ): Map<Double, StoryStep>? {
        val mutable = currentStory.toSortedMutableMap()
        val (position, spreadsheet) = mutable.entries.find { it.value.id == spreadsheetId }
            ?: return null

        val rows = spreadsheet.steps
        if (rows.isEmpty()) return null

        val columnCount = rows.firstOrNull()?.steps?.size ?: return null
        val insertIndex = columnIndex.coerceIn(0, columnCount)

        val updatedRows = rows.map { row ->
            val cells = row.steps.toMutableList()
            val newCell = StoryStep(type = StoryTypes.SPREADSHEET_CELL.type, text = "")
            cells.add(insertIndex, newCell)
            row.copy(steps = cells)
        }

        val updatedSpreadsheet = spreadsheet.copy(steps = updatedRows)
        mutable[position] = updatedSpreadsheet
        return mutable
    }

    fun moveSpreadsheetRow(
        currentStory: Map<Double, StoryStep>,
        spreadsheetId: String,
        fromIndex: Int,
        toIndex: Int
    ): Map<Double, StoryStep>? {
        val mutable = currentStory.toSortedMutableMap()
        val (position, spreadsheet) = mutable.entries.find { it.value.id == spreadsheetId }
            ?: return null

        val rows = spreadsheet.steps.toMutableList()
        if (fromIndex < 0 || fromIndex >= rows.size) return null
        if (toIndex < 0 || toIndex >= rows.size) return null
        if (fromIndex == toIndex) return null

        val row = rows.removeAt(fromIndex)
        rows.add(toIndex, row)

        val updatedSpreadsheet = spreadsheet.copy(steps = rows)
        mutable[position] = updatedSpreadsheet
        return mutable
    }

    fun moveSpreadsheetColumn(
        currentStory: Map<Double, StoryStep>,
        spreadsheetId: String,
        fromIndex: Int,
        toIndex: Int
    ): Map<Double, StoryStep>? {
        val mutable = currentStory.toSortedMutableMap()
        val (position, spreadsheet) = mutable.entries.find { it.value.id == spreadsheetId }
            ?: return null

        val rows = spreadsheet.steps
        if (rows.isEmpty()) return null

        val columnCount = rows.firstOrNull()?.steps?.size ?: return null
        if (fromIndex < 0 || fromIndex >= columnCount) return null
        if (toIndex < 0 || toIndex >= columnCount) return null
        if (fromIndex == toIndex) return null

        val updatedRows = rows.map { row ->
            val cells = row.steps.toMutableList()
            val cell = cells.removeAt(fromIndex)
            cells.add(toIndex, cell)
            row.copy(steps = cells)
        }

        val updatedSpreadsheet = spreadsheet.copy(steps = updatedRows)
        mutable[position] = updatedSpreadsheet
        return mutable
    }

    /**
     * Parses column widths from the spreadsheet metadata JSON.
     * Returns a list of widths with the specified column count, using defaults for missing values.
     */
    private fun parseColumnWidths(text: String?, columnCount: Int): List<Int> {
        val defaultWidth = 120
        if (text.isNullOrBlank()) {
            return List(columnCount) { defaultWidth }
        }

        return try {
            // Simple regex to extract the columnWidths array: {"columnWidths":[120,150,100]}
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
                widths.getOrElse(index) { defaultWidth }
            }
        } catch (e: Exception) {
            List(columnCount) { defaultWidth }
        }
    }
}
