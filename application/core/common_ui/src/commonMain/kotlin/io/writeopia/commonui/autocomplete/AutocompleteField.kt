package io.writeopia.commonui.autocomplete

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

private val MIN_DROPDOWN_WIDTH = 112.dp


@Composable
fun <T> AutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    search: suspend (query: String) -> List<T>,
    suggestionToText: (T) -> String,
    suggestionContent: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    onSuggestionSelect: (T) -> Unit = {},
    searchKey: Any? = null,
    placeholder: @Composable (() -> Unit)? = null,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall
        .copy(color = MaterialTheme.colorScheme.onBackground),
    minQueryLength: Int = 2,
    debounceMillis: Long = 300L,
    normalizeQuery: (String) -> String = { it.trim() },
) {
    var suggestions by remember { mutableStateOf<List<T>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    var shouldSearch by remember { mutableStateOf(true) }
    var selectedIndex by remember { mutableStateOf(-1) }
    val density = LocalDensity.current
    var fieldWidth by remember { mutableStateOf(0.dp) }

    fun closePopup() {
        isExpanded = false
        selectedIndex = -1
    }

    fun select(item: T) {
        // Stop the search from running again when the field text is set to the chosen suggestion.
        shouldSearch = false
        closePopup()
        onValueChange(suggestionToText(item))
        onSuggestionSelect(item)
    }

    LaunchedEffect(value, searchKey) {
        val query = normalizeQuery(value)
        if (query.length < minQueryLength) {
            suggestions = emptyList()
            closePopup()
            return@LaunchedEffect
        }
        if (!shouldSearch) return@LaunchedEffect

        delay(debounceMillis)
        isSearching = true
        try {
            suggestions = search(query)
            isExpanded = suggestions.isNotEmpty()
            selectedIndex = -1
        } finally {
            isSearching = false
        }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                shouldSearch = true
                selectedIndex = -1
                onValueChange(it)
            },
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    fieldWidth = with(density) { coordinates.size.width.toDp() }
                }
                // for power users like me :)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || !isExpanded) return@onPreviewKeyEvent false

                    when (event.key) {
                        Key.DirectionDown -> {
                            if (suggestions.isEmpty()) return@onPreviewKeyEvent false
                            selectedIndex = if (selectedIndex < suggestions.lastIndex) selectedIndex + 1 else 0
                            true
                        }

                        Key.DirectionUp -> {
                            if (suggestions.isEmpty()) return@onPreviewKeyEvent false
                            selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else suggestions.lastIndex
                            true
                        }

                        Key.Enter, Key.NumPadEnter -> {
                            val item = suggestions.getOrNull(selectedIndex) ?: return@onPreviewKeyEvent false
                            select(item)
                            true
                        }

                        Key.Escape -> {
                            closePopup()
                            true
                        }

                        else -> false
                    }
                },
            shape = MaterialTheme.shapes.large,
            singleLine = true,
            placeholder = placeholder,
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            },
            textStyle = textStyle
        )

        SuggestionsDropdown(
            expanded = isExpanded,
            suggestions = suggestions,
            selectedIndex = selectedIndex,
            minWidth = fieldWidth,
            onSelect = ::select,
            onDismiss = ::closePopup,
            itemContent = suggestionContent,
        )
    }
}

@Composable
private fun <T> SuggestionsDropdown(
    expanded: Boolean,
    suggestions: List<T>,
    selectedIndex: Int,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    itemContent: @Composable (T) -> Unit,
    minWidth: Dp = MIN_DROPDOWN_WIDTH,
) {
    DropdownMenu(
        expanded = expanded && suggestions.isNotEmpty(),
        onDismissRequest = onDismiss,
        modifier = Modifier.requiredWidthIn(
            min = if (minWidth > 0.dp) minWidth else MIN_DROPDOWN_WIDTH,
            max = Dp.Unspecified
        ),
        
        properties = PopupProperties(focusable = false)
    ) {
        suggestions.forEachIndexed { index, item ->
            DropdownMenuItem(
                modifier = Modifier
                    .requiredWidthIn(min = MIN_DROPDOWN_WIDTH, max = Dp.Unspecified)
                    .background(
                        if (index == selectedIndex) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            Color.Transparent
                        }
                    ),
                text = { itemContent(item) },
                onClick = { onSelect(item) }
            )
        }
    }
}
