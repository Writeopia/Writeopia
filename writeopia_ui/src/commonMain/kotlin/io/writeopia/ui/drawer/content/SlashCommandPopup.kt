package io.writeopia.ui.drawer.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.HighlightAlt
import io.writeopia.sdk.models.command.CommandFactory
import io.writeopia.sdk.models.command.CommandInfo
import io.writeopia.sdk.models.command.CommandTrigger
import io.writeopia.sdk.models.command.TypeInfo
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.writeopia.ui.utils.getCurrentDateFormatted
import io.writeopia.ui.utils.getCurrentDateTimeFormatted

/**
 * Represents a slash command that can be triggered by typing "/" in the editor.
 *
 * @param name The display name of the command
 * @param description A short description of what the command does
 * @param icon The icon to display for the command
 * @param commandText The text that triggers the command (e.g., "/today")
 * @param action The action to perform when the command is selected. Receives the current position.
 *               Returns the text to insert (if any), or null if the action handles everything.
 */
data class SlashCommand(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val commandText: String,
    val action: (position: Int) -> String?
)

val defaultSlashCommands = listOf(
    SlashCommand(
        name = "Today",
        description = "Insert today's date",
        icon = Icons.Outlined.CalendarToday,
        commandText = "/today",
        action = { getCurrentDateFormatted() }
    ),
    SlashCommand(
        name = "Now",
        description = "Insert current date and time",
        icon = Icons.Outlined.AccessTime,
        commandText = "/now",
        action = { getCurrentDateTimeFormatted() }
    )
)

/**
 * Creates slash commands that change the story type.
 * These commands require access to the state manager functions.
 *
 * @param onTypeChange Callback to change story type (position, typeInfo, commandInfo)
 * @param onTagToggle Callback to toggle a tag (position, tagInfo, commandInfo)
 */
fun createTypeCommands(
    onTypeChange: (Int, TypeInfo, CommandInfo) -> Unit,
    onTagToggle: (Int, TagInfo, CommandInfo) -> Unit
): List<SlashCommand> = listOf(
    SlashCommand(
        name = "Checkbox",
        description = "Change line to a checkbox",
        icon = Icons.Outlined.CheckBox,
        commandText = "/checkbox",
        action = { position ->
            onTypeChange(
                position,
                TypeInfo(StoryTypes.CHECK_ITEM.type),
                CommandInfo(CommandFactory.checkItem(), CommandTrigger.CLICKED)
            )
            null
        }
    ),
    SlashCommand(
        name = "List",
        description = "Change line to a bullet list",
        icon = Icons.Outlined.FormatListBulleted,
        commandText = "/list",
        action = { position ->
            onTypeChange(
                position,
                TypeInfo(StoryTypes.UNORDERED_LIST_ITEM.type),
                CommandInfo(CommandFactory.unOrderedList(), CommandTrigger.CLICKED)
            )
            null
        }
    ),
    SlashCommand(
        name = "Box",
        description = "Change line to a highlight box",
        icon = Icons.Outlined.HighlightAlt,
        commandText = "/box",
        action = { position ->
            onTagToggle(
                position,
                TagInfo(Tag.HIGH_LIGHT_BLOCK),
                CommandInfo(CommandFactory.box(), CommandTrigger.CLICKED)
            )
            null
        }
    )
)

@Composable
fun SlashCommandPopup(
    filter: String,
    commands: List<SlashCommand> = defaultSlashCommands,
    onCommandSelected: (SlashCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredCommands = if (filter.isEmpty()) {
        commands
    } else {
        commands.filter { command ->
            command.name.contains(filter, ignoreCase = true) ||
                command.commandText.contains(filter, ignoreCase = true)
        }
    }

    if (filteredCommands.isEmpty()) return

    Card(
        modifier = modifier.width(280.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = "Commands",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            filteredCommands.forEach { command ->
                SlashCommandItem(
                    command = command,
                    onClick = { onCommandSelected(command) }
                )
            }
        }
    }
}

@Composable
private fun SlashCommandItem(
    command: SlashCommand,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = command.icon,
            contentDescription = command.name,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(end = 12.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = command.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = command.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
