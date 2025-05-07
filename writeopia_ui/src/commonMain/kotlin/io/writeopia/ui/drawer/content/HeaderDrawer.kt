package io.writeopia.ui.drawer.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.models.story.Decoration
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.drawer.SimpleTextDrawer
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.drawer.factory.EndOfText
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.DrawConfig
import io.writeopia.ui.model.DrawInfo
import io.writeopia.ui.model.EmptyErase
import io.writeopia.ui.utils.transparentTextInputColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import writeopia.writeopia_ui.generated.resources.Res
import writeopia.writeopia_ui.generated.resources.title

/**
 * The header for the Document. It applies some stylish to the title of the document.
 */
class HeaderDrawer(
    private val modifier: Modifier = Modifier,
    private val headerClick: () -> Unit = {},
    private val textDrawer: () -> SimpleTextDrawer,
//    private val multipleSelection: (Int) -> Unit,
    private val placeHolderStyle: @Composable () -> TextStyle = {
        MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.Bold,
            color = Color.LightGray,
        )
    },
) : StoryStepDrawer {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val backgroundColor = step.decoration.backgroundColor
        val focusRequester = remember { FocusRequester() }

        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()

        Box(modifier = Modifier.hoverable(interactionSource).padding(bottom = 16.dp)) {
            Row(
                modifier = modifier
                    .let { modifierLet ->
                        if (backgroundColor != null) {
                            modifierLet
                                .background(Color(backgroundColor))
                                .padding(top = 114.dp)
                        } else {
                            modifierLet.padding(top = 14.dp)
                        }
                    }
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val interactionSource = remember { MutableInteractionSource() }

                val extraData = drawInfo.extraData

                if (extraData.containsKey("imageVector")) {
                    val imageVector = extraData["imageVector"] as ImageVector

                    val tint = if (extraData.containsKey("imageVectorTint")) {
                        val tintColor = extraData["imageVectorTint"] as Int
                        Color(tintColor)
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Icon(
                        imageVector = imageVector,
                        "header icon",
                        tint = tint,
                        modifier = Modifier.size(42.dp)
                    )
                }

                textDrawer().Text(
                    step = step,
                    drawInfo = drawInfo,
                    interactionSource = interactionSource,
                    focusRequester = focusRequester,
                    decorationBox = @Composable { innerTextField ->
                        TextFieldDefaults.DecorationBox(
                            value = step.text ?: "",
                            innerTextField = innerTextField,
                            enabled = true,
                            singleLine = false,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = interactionSource,
                            placeholder = {
                                Text(
                                    text = stringResource(Res.string.title),
                                    style = placeHolderStyle(),
                                )
                            },
                            colors = transparentTextInputColors(),
                        )
                    },
                )
            }

            AnimatedVisibility(
                isHovered,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.TopEnd),
            ) {
                Text(
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClick = headerClick)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.medium
                        )
                        .padding(6.dp),
                    text = "Edit header",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun headerDrawer(
    manager: WriteopiaStateManager,
    headerClick: () -> Unit,
    onKeyEvent: (KeyEvent, TextFieldValue, StoryStep, Int, EmptyErase, Int, EndOfText) -> Boolean,
    modifier: Modifier = Modifier,
    lineBreakByContent: Boolean,
    selectionState: StateFlow<Boolean>,
    drawConfig: DrawConfig,
    enabled: Boolean = true,
    isDarkTheme: Boolean,
    fontFamily: FontFamily? = null
): StoryStepDrawer =
    HeaderDrawer(
        modifier = modifier,
        textDrawer = {
            TextDrawer(
                modifier = Modifier.padding(start = drawConfig.textDrawerStartPadding.dp),
                enabled = enabled,
                isDarkTheme = isDarkTheme,
                onTextEdit = manager::handleTextInput,
                onKeyEvent = onKeyEvent,
                lineBreakByContent = lineBreakByContent,
                emptyErase = EmptyErase.DISABLED,
                textStyle = { drawConfig.titleStyle(fontFamily) },
                selectionState = selectionState,
                onSelectionLister = {}
            )
        },
        headerClick = headerClick,
        placeHolderStyle = { drawConfig.titlePlaceHolderStyle(fontFamily) }
    )

@Preview
@Composable
private fun HeaderDrawerStepPreview() {
    val step = sampleStoryStep()

    HeaderDrawer(
        textDrawer = {
            TextDrawer(
                modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
                isDarkTheme = true,
                onTextEdit = { _, _, _ -> },
                textStyle = {
                    MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                selectionState = MutableStateFlow(false),
                onSelectionLister = {}
            )
        },
        headerClick = {},
    ).Step(step = step, drawInfo = DrawInfo())
}

@Preview
@Composable
private fun HeaderDrawerStepPreviewNoColor() {
    val step = sampleStoryStep()

    HeaderDrawer(
        textDrawer = {
            TextDrawer(
                modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
                isDarkTheme = true,
                onTextEdit = { _, _, _ -> },
                textStyle = {
                    MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                selectionState = MutableStateFlow(false),
                onSelectionLister = {}
            )
        },
        headerClick = {},
    ).Step(step = step, drawInfo = DrawInfo())
}

private fun sampleStoryStep() = StoryStep(
    type = StoryTypes.TITLE.type,
    decoration = Decoration(
        backgroundColor = Color.Blue.toArgb(),
    ),
    text = "Document Title",
)
