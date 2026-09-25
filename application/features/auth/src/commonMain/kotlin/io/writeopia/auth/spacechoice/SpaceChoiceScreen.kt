package io.writeopia.auth.spacechoice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
    var isPrivateHovered by remember { mutableStateOf(false) }
    var isOpenHovered by remember { mutableStateOf(false) }

    val screenBackground = WriteopiaTheme.colorScheme.globalBackground
    val targetBackground = when {
        isPrivateHovered -> lerp(screenBackground, Color.Black, 0.8f)
        isOpenHovered -> lerp(screenBackground, Color.White, 0.3f)
        else -> screenBackground
    }
    val animatedBackground by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 200)
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(animatedBackground)) {
        val isWide = maxWidth > maxHeight

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 1000.dp)
                .fillMaxSize()
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
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SpaceCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        label = WrStrings.privateSpaceLabel(),
                        title = WrStrings.privateSpaceTitle(),
                        description = WrStrings.privateSpaceDescription(),
                        chips = OFFLINE_MODELS,
                        onHoveredChange = { isPrivateHovered = it },
                        onClick = onOfflineSelected
                    )

                    SpaceCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        label = WrStrings.openSpaceLabel(),
                        title = WrStrings.openSpaceTitle(),
                        description = WrStrings.openSpaceDescription(),
                        chips = ONLINE_MODELS,
                        onHoveredChange = { isOpenHovered = it },
                        onClick = onOnlineSelected
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SpaceCard(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        label = WrStrings.privateSpaceLabel(),
                        title = WrStrings.privateSpaceTitle(),
                        description = WrStrings.privateSpaceDescription(),
                        chips = OFFLINE_MODELS,
                        onHoveredChange = { isPrivateHovered = it },
                        onClick = onOfflineSelected
                    )

                    SpaceCard(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        label = WrStrings.openSpaceLabel(),
                        title = WrStrings.openSpaceTitle(),
                        description = WrStrings.openSpaceDescription(),
                        chips = ONLINE_MODELS,
                        onHoveredChange = { isOpenHovered = it },
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
    onHoveredChange: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val shape = MaterialTheme.shapes.large
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(isHovered) {
        onHoveredChange(isHovered)
    }

    val borderColor = if (isHovered) {
        accentColor
    } else {
        WriteopiaTheme.colorScheme.dividerColor
    }

    val titleScale by animateFloatAsState(
        targetValue = if (isHovered) 1.06f else 1f,
        animationSpec = tween(durationMillis = 200)
    )

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
                fontWeight = FontWeight.Bold,
                modifier = Modifier.scale(titleScale)
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

        Spacer(modifier = Modifier.weight(1f))

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
