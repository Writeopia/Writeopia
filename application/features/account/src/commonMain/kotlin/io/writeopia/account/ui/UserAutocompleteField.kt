package io.writeopia.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import io.writeopia.app.dto.SearchUserApi
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.theme.WriteopiaTheme

private val MIN_DROPDOWN_WIDTH = 112.dp

@Composable
internal fun UserAutocompleteField(
    value: String,
    suggestions: ResultData<List<SearchUserApi>>,
    onValueChange: (String) -> Unit,
    onSuggestionSelect: (SearchUserApi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = (suggestions as? ResultData.Complete)?.data ?: emptyList()
    val isSearching = suggestions is ResultData.Loading || suggestions is ResultData.InProgress

    var isExpanded by remember(suggestions) { mutableStateOf(items.isNotEmpty()) }
    var selectedIndex by remember(suggestions) { mutableStateOf(-1) }
    val density = LocalDensity.current
    var fieldWidth by remember { mutableStateOf(0.dp) }

    fun closePopup() {
        isExpanded = false
        selectedIndex = -1
    }

    fun select(item: SearchUserApi) {
        closePopup()
        onSuggestionSelect(item)
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
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
                            if (items.isEmpty()) return@onPreviewKeyEvent false
                            selectedIndex = if (selectedIndex < items.lastIndex) selectedIndex + 1 else 0
                            true
                        }

                        Key.DirectionUp -> {
                            if (items.isEmpty()) return@onPreviewKeyEvent false
                            selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else items.lastIndex
                            true
                        }

                        Key.Enter, Key.NumPadEnter -> {
                            val item = items.getOrNull(selectedIndex) ?: return@onPreviewKeyEvent false
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
            placeholder = {
                BasicText(
                    WrStrings.username(),
                    style = MaterialTheme
                        .typography
                        .titleSmall
                        .copy(color = WriteopiaTheme.colorScheme.textLighter)
                )
            },
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            },
            textStyle = MaterialTheme.typography.titleSmall
                .copy(color = MaterialTheme.colorScheme.onBackground)
        )

        UserSuggestionsDropdown(
            expanded = isExpanded,
            suggestions = items,
            selectedIndex = selectedIndex,
            minWidth = fieldWidth,
            onSelect = ::select,
            onDismiss = ::closePopup,
        )
    }
}

@Composable
private fun UserSuggestionsDropdown(
    expanded: Boolean,
    suggestions: List<SearchUserApi>,
    selectedIndex: Int,
    onSelect: (SearchUserApi) -> Unit,
    onDismiss: () -> Unit,
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
        suggestions.forEachIndexed { index, user ->
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
                text = { UserSuggestionItem(user) },
                onClick = { onSelect(user) }
            )
        }
    }
}

@Composable
private fun UserSuggestionItem(user: SearchUserApi) {
    Column {
        Text(
            text = user.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = "@${user.username}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}
