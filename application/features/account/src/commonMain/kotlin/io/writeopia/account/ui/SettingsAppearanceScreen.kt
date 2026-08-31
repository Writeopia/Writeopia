package io.writeopia.account.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.model.AccentColor
import io.writeopia.model.ColorThemeOption
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

private const val SPACE_AFTER_TITLE = 12

@Composable
fun SettingsAppearanceScreen(
    selectedColorTheme: StateFlow<ColorThemeOption?>,
    selectedAccentColor: StateFlow<AccentColor?>,
    selectColorTheme: (ColorThemeOption) -> Unit,
    selectAccentColor: (AccentColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ColorThemeOptions(
            selectedColorTheme = selectedColorTheme,
            selectColorTheme = selectColorTheme
        )

        Spacer(modifier = Modifier.height(24.dp))

        AccentColorOptions(
            selectedAccentColor = selectedAccentColor,
            selectAccentColor = selectAccentColor
        )
    }
}

@Composable
private fun ColorThemeOptions(
    selectedColorTheme: StateFlow<ColorThemeOption?>,
    selectColorTheme: (ColorThemeOption) -> Unit
) {
    val titleStyle = MaterialTheme.typography.titleLarge
    val titleColor = MaterialTheme.colorScheme.onBackground
    val selectedTheme by selectedColorTheme.collectAsState()

    Column {
        Text(WrStrings.colorTheme(), style = titleStyle, color = titleColor)

        Spacer(modifier = Modifier.height(SPACE_AFTER_TITLE.dp))

        val spaceWidth = 10.dp

        Row(modifier = Modifier.fillMaxWidth().height(70.dp)) {
            Option(
                text = WrStrings.lightTheme(),
                imageVector = WrIcons.colorModeLight,
                contextDescription = WrStrings.lightTheme(),
                isSelected = selectedTheme == ColorThemeOption.LIGHT,
                selectColorTheme = {
                    selectColorTheme(ColorThemeOption.LIGHT)
                }
            )

            Spacer(modifier = Modifier.width(spaceWidth))

            Option(
                text = WrStrings.darkTheme(),
                imageVector = WrIcons.colorModeDark,
                contextDescription = WrStrings.darkTheme(),
                isSelected = selectedTheme == ColorThemeOption.DARK,
                selectColorTheme = {
                    selectColorTheme(ColorThemeOption.DARK)
                }
            )

            Spacer(modifier = Modifier.width(spaceWidth))

            Option(
                text = WrStrings.systemTheme(),
                imageVector = WrIcons.colorModeSystem,
                contextDescription = WrStrings.systemTheme(),
                isSelected = selectedTheme == ColorThemeOption.SYSTEM,
                selectColorTheme = {
                    selectColorTheme(ColorThemeOption.SYSTEM)
                }
            )
        }
    }
}

@Composable
private fun RowScope.Option(
    text: String,
    imageVector: ImageVector,
    contextDescription: String,
    isSelected: Boolean,
    selectColorTheme: () -> Unit
) {
    val typography = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
    val textColor = WriteopiaTheme.colorScheme.textLight

    val iconTint = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        WriteopiaTheme.colorScheme.tintLight
    }

    val iconSize by animateDpAsState(
        targetValue = if (isSelected) 30.dp else 24.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    val weight by animateFloatAsState(
        targetValue = if (isSelected) 1.5f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(WriteopiaTheme.colorScheme.optionsSelector)
            .clickable(onClick = selectColorTheme)
            .fillMaxHeight()
            .weight(weight)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                modifier = Modifier.size(iconSize),
                imageVector = imageVector,
                contentDescription = contextDescription,
                tint = iconTint
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(text, style = typography, color = textColor)
        }
    }
}

@Composable
private fun AccentColorOptions(
    selectedAccentColor: StateFlow<AccentColor?>,
    selectAccentColor: (AccentColor) -> Unit
) {
    val titleStyle = MaterialTheme.typography.titleLarge
    val titleColor = MaterialTheme.colorScheme.onBackground
    val selectedColor by selectedAccentColor.collectAsState()

    Column {
        Text(WrStrings.accentColor(), style = titleStyle, color = titleColor)

        Spacer(modifier = Modifier.height(SPACE_AFTER_TITLE.dp))

        val spaceWidth = 10.dp

        Row(modifier = Modifier.fillMaxWidth().height(70.dp)) {
            AccentColorOption(
                accentColor = AccentColor.PURPLE,
                isSelected = selectedColor == AccentColor.PURPLE,
                onClick = { selectAccentColor(AccentColor.PURPLE) }
            )

            Spacer(modifier = Modifier.width(spaceWidth))

            AccentColorOption(
                accentColor = AccentColor.BLUE,
                isSelected = selectedColor == AccentColor.BLUE,
                onClick = { selectAccentColor(AccentColor.BLUE) }
            )

            Spacer(modifier = Modifier.width(spaceWidth))

            AccentColorOption(
                accentColor = AccentColor.ORANGE,
                isSelected = selectedColor == AccentColor.ORANGE,
                onClick = { selectAccentColor(AccentColor.ORANGE) }
            )
        }
    }
}

@Composable
private fun RowScope.AccentColorOption(
    accentColor: AccentColor,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val typography = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
    val textColor = WriteopiaTheme.colorScheme.textLight

    val circleSize by animateDpAsState(
        targetValue = if (isSelected) 30.dp else 24.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    val weight by animateFloatAsState(
        targetValue = if (isSelected) 1.5f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(WriteopiaTheme.colorScheme.optionsSelector)
            .clickable(onClick = onClick)
            .fillMaxHeight()
            .weight(weight)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(circleSize)
                    .clip(CircleShape)
                    .background(accentColor.lightColor)
                    .then(
                        if (isSelected) {
                            Modifier.border(borderWidth, MaterialTheme.colorScheme.onBackground, CircleShape)
                        } else {
                            Modifier
                        }
                    )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                accentColor.id.replaceFirstChar { it.uppercase() },
                style = typography,
                color = textColor
            )
        }
    }
}
