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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.writeopia.sdk.models.story.Decoration
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.drawer.SimpleTextDrawer
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.drawer.factory.EndOfText
import io.writeopia.ui.icons.WrSdkIcons
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.DrawConfig
import io.writeopia.ui.model.DrawInfo
import io.writeopia.ui.model.EmptyErase
import io.writeopia.ui.utils.OutlinedTitle
import io.writeopia.ui.utils.Spans
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
    private val forceEditorButtonAppear: Boolean = false,
    /** False for a document that can't be edited (locked, a slide of a presentation): no edit button. */
    private val showsEditButton: Boolean = true,
    private val headerClick: () -> Unit = {},
    private val textDrawer: () -> SimpleTextDrawer,
    /** The theme, which picks the outline of the title: dark on a dark theme, light on a light one. */
    private val isDarkTheme: Boolean = false,
    /** The style of the title, which the outline behind it copies so both line up. */
    private val titleStyle: @Composable () -> TextStyle = {
        MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    },
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
        // A picture in the header takes the place of its color.
        val imageUrl = step.url?.takeIf { it.isNotBlank() }
        val backgroundColor = step.decoration.backgroundColor.takeIf { imageUrl == null }
        val focusRequester = remember { FocusRequester() }

        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()

        Box(modifier = Modifier.hoverable(interactionSource).padding(bottom = 16.dp)) {
            Column {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Header image",
                        contentScale = ContentScale.Crop,
                        modifier = modifier
                            .fillMaxWidth()
                            .height(HEADER_IMAGE_HEIGHT)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }

                Row(
                    modifier = (if (imageUrl != null) Modifier else modifier)
                        .let { modifierLet ->
                            if (backgroundColor != null) {
                                modifierLet
                                    .background(Color(backgroundColor))
                                    .padding(top = 131.dp)
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
                                innerTextField = {
                                    // Over a color or a picture the title gets an outline, so
                                    // it reads on any background.
                                    if (backgroundColor != null || imageUrl != null) {
                                        OutlinedTitle(
                                            text = Spans.createStringWithSpans(
                                                step.text,
                                                step.spans,
                                                isDarkTheme
                                            ),
                                            style = titleStyle(),
                                            isDarkTheme = isDarkTheme,
                                            content = innerTextField,
                                        )
                                    } else {
                                        innerTextField()
                                    }
                                },
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
            }

            AnimatedVisibility(
                showsEditButton && (isHovered || forceEditorButtonAppear),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.TopEnd),
            ) {
                Icon(
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClick = headerClick)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.medium
                        )
                        .padding(8.dp)
                        .size(16.dp),
                    imageVector = WrSdkIcons.edit,
                    contentDescription = "Edit header",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

/** The height of the picture of a header, a band above the title. */
private val HEADER_IMAGE_HEIGHT = 230.dp

fun headerDrawer(
    manager: WriteopiaStateManager,
    headerClick: () -> Unit,
    onKeyEvent: (KeyEvent, TextFieldValue, StoryStep, String, EmptyErase, Int, EndOfText) -> Boolean,
    modifier: Modifier = Modifier,
    lineBreakByContent: Boolean,
    selectionState: StateFlow<Boolean>,
    drawConfig: DrawConfig,
    enabled: Boolean = true,
    isDarkTheme: Boolean,
    fontFamily: FontFamily? = null,
    forceEditorButtonAppear: Boolean = false,
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
                onSelectionLister = {},
                aiExplanation = "",
            )
        },
        headerClick = headerClick,
        forceEditorButtonAppear = forceEditorButtonAppear,
        showsEditButton = enabled,
        placeHolderStyle = { drawConfig.titlePlaceHolderStyle(fontFamily) },
        isDarkTheme = isDarkTheme,
        titleStyle = { drawConfig.titleStyle(fontFamily) },
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
                onSelectionLister = {},
                aiExplanation = ""
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
                onSelectionLister = {},
                aiExplanation = ""
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
