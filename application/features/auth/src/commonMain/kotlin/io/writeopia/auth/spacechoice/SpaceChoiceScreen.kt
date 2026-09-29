package io.writeopia.auth.spacechoice

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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme

const val PRIVATE_SPACE_CARD_TAG = "privateSpaceCard"

private val OFFLINE_MODELS = listOf("llama3", "mistral", "deepseek-r1")
private val ONLINE_MODELS = listOf("claude", "gemini", "gpt")
private val PAGE_PADDING = 24.dp
private val CARDS_SPACING = 16.dp

/** How much of the light above the window reaches the screen at rest. */
private const val LIGHT_AT_REST = 0.35f
private const val DARK_ROOM_ALPHA = 0.55f
private val LIGHT_RADIUS = 900.dp

@Composable
fun SpaceChoiceScreen(
    modifier: Modifier = Modifier,
    onOfflineSelected: () -> Unit,
    onOnlineSelected: () -> Unit,
) {
    var isPrivateHovered by remember { mutableStateOf(false) }
    var isOpenHovered by remember { mutableStateOf(false) }

    val screenBackground = WriteopiaTheme.colorScheme.globalBackground
    // A light hangs above the window: only its glow reaches the screen. Dimmed at rest, brighter
    // over the open space, and off while the private space darkens the room.
    val lightIntensity by animateFloatAsState(
        targetValue = when {
            isPrivateHovered -> 0f
            isOpenHovered -> 1f
            else -> LIGHT_AT_REST
        },
        animationSpec = tween(durationMillis = 300)
    )
    val darkness by animateFloatAsState(
        targetValue = if (isPrivateHovered) DARK_ROOM_ALPHA else 0f,
        animationSpec = tween(durationMillis = 300)
    )
    val lightRadius = with(LocalDensity.current) { LIGHT_RADIUS.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(screenBackground)
            .drawBehind {
                if (darkness > 0f) {
                    drawRect(Color.Black.copy(alpha = darkness))
                }
                if (lightIntensity > 0f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.55f * lightIntensity),
                                Color.White.copy(alpha = 0.18f * lightIntensity),
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2f, -0.35f * size.height),
                            radius = lightRadius
                        )
                    )
                }
            }
    ) {
        val isWide = maxWidth > maxHeight
        val density = LocalDensity.current
        var headerHeight by remember { mutableStateOf(0.dp) }

        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 1000.dp)
                .fillMaxSize()
                .padding(WindowInsets.systemBars.asPaddingValues())
        ) {
            // The page scrolls, so the cards can't just fill the height: they get at least the
            // height left under the header and grow past it when their content needs more.
            val cardsMinHeight = (maxHeight - PAGE_PADDING * 2 - headerHeight).coerceAtLeast(0.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(PAGE_PADDING),
            ) {
                Column(
                    modifier = Modifier.onSizeChanged { size ->
                        headerHeight = with(density) { size.height.toDp() }
                    }
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
                }

                if (isWide) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = cardsMinHeight)
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(CARDS_SPACING)
                    ) {
                        SpaceCard(
                            modifier = Modifier.weight(1f).fillMaxHeight().testTag(PRIVATE_SPACE_CARD_TAG),
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
                    val cardMinHeight = ((cardsMinHeight - CARDS_SPACING) / 2).coerceAtLeast(0.dp)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(CARDS_SPACING)
                    ) {
                        SpaceCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = cardMinHeight)
                                .testTag(PRIVATE_SPACE_CARD_TAG),
                            label = WrStrings.privateSpaceLabel(),
                            title = WrStrings.privateSpaceTitle(),
                            description = WrStrings.privateSpaceDescription(),
                            chips = OFFLINE_MODELS,
                            onHoveredChange = { isPrivateHovered = it },
                            onClick = onOfflineSelected
                        )

                        SpaceCard(
                            modifier = Modifier.fillMaxWidth().heightIn(min = cardMinHeight),
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
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            color = WriteopiaTheme.colorScheme.textLight,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            // Grows from its left edge, so the text doesn't shift.
            modifier = Modifier.graphicsLayer {
                scaleX = titleScale
                scaleY = titleScale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
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
