package io.writeopia.auth.spacechoice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme

private val OFFLINE_MODELS = listOf("llama3", "mistral", "deepseek-r1")
private val ONLINE_MODELS = listOf("claude", "gemini", "gpt")

@Composable
fun SpaceChoiceScreen(
    modifier: Modifier = Modifier,
    onOfflineSelected: () -> Unit,
    onOnlineSelected: () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth > maxHeight

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 1000.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                text = WrStrings.chooseYourSpace().uppercase(),
                color = WriteopiaTheme.colorScheme.textLighter,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = WrStrings.whereWritingToday(),
                color = WriteopiaTheme.colorScheme.textLight,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (isWide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SpaceCard(
                        modifier = Modifier.weight(1f),
                        label = WrStrings.privateSpaceLabel(),
                        title = WrStrings.privateSpaceTitle(),
                        description = WrStrings.privateSpaceDescription(),
                        chips = OFFLINE_MODELS,
                        onClick = onOfflineSelected
                    )

                    SpaceCard(
                        modifier = Modifier.weight(1f),
                        label = WrStrings.openSpaceLabel(),
                        title = WrStrings.openSpaceTitle(),
                        description = WrStrings.openSpaceDescription(),
                        chips = ONLINE_MODELS,
                        onClick = onOnlineSelected
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SpaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        label = WrStrings.privateSpaceLabel(),
                        title = WrStrings.privateSpaceTitle(),
                        description = WrStrings.privateSpaceDescription(),
                        chips = OFFLINE_MODELS,
                        onClick = onOfflineSelected
                    )

                    SpaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        label = WrStrings.openSpaceLabel(),
                        title = WrStrings.openSpaceTitle(),
                        description = WrStrings.openSpaceDescription(),
                        chips = ONLINE_MODELS,
                        onClick = onOnlineSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun SpaceCard(
    modifier: Modifier = Modifier,
    label: String,
    title: String,
    description: String,
    chips: List<String>,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val shape = MaterialTheme.shapes.large
    val accentColor = MaterialTheme.colorScheme.primary

    val borderColor = if (isHovered) {
        accentColor
    } else {
        WriteopiaTheme.colorScheme.dividerColor
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(WriteopiaTheme.colorScheme.cardBg, shape)
            .border(1.dp, borderColor, shape)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = label.uppercase(),
                color = accentColor,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            color = WriteopiaTheme.colorScheme.textLight,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = description,
            color = WriteopiaTheme.colorScheme.textLighter,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { chip ->
                Text(
                    text = chip,
                    color = WriteopiaTheme.colorScheme.textLighter,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .border(1.dp, WriteopiaTheme.colorScheme.dividerColor, MaterialTheme.shapes.small)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = WrStrings.enterArrow(),
                color = accentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
