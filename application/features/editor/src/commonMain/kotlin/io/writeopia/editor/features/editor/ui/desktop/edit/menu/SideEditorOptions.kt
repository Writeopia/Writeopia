package io.writeopia.editor.features.editor.ui.desktop.edit.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.writeopia.common.utils.collections.inBatches
import io.writeopia.common.utils.file.fileChooserLoad
import io.writeopia.common.utils.file.fileChooserSave
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.editor.features.editor.viewmodel.SideMenuTab
import io.writeopia.model.Font
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.story.Tag
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.icons.WrSdkIcons
import io.writeopia.ui.model.SelectionMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SideEditorOptions(
    modifier: Modifier = Modifier,
    alignment: Alignment,
    isDarkTheme: Boolean,
    currentModel: Flow<String>,
    models: Flow<List<String>>,
    fontStyleSelected: () -> StateFlow<Font>,
    isEditableState: StateFlow<Boolean>,
    isFavorite: StateFlow<Boolean>,
    selectedMetadataState: StateFlow<Set<SelectionMetadata>>,
    sideMenuTabState: StateFlow<SideMenuTab>,
    boldClick: (Span) -> Unit,
    setEditable: () -> Unit,
    checkItemClick: () -> Unit,
    listItemClick: () -> Unit,
    codeBlockClick: () -> Unit,
    highLightBlockClick: () -> Unit,
    onPresentationClick: () -> Unit,
    changeFontFamily: (Font) -> Unit,
    addImage: (String) -> Unit,
    exportJson: (String) -> Unit,
    exportMarkdown: (String) -> Unit,
    moveToRoot: () -> Unit,
    moveToClick: () -> Unit,
    askAiBySelection: () -> Unit,
    aiSummary: () -> Unit,
    aiActionPoints: () -> Unit,
    aiFaq: () -> Unit,
    aiTags: () -> Unit,
    addPage: () -> Unit,
    deleteDocument: () -> Unit,
    toggleFavorite: () -> Unit,
    selectModel: (String) -> Unit,
    changeSideMenuTab: (SideMenuTab) -> Unit,
    titleClick: (Tag) -> Unit
) {
    val menuType by sideMenuTabState.collectAsState()

    val showSubMenu by remember {
        derivedStateOf {
            menuType != SideMenuTab.NONE
        }
    }

    Popup(
        alignment = alignment,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
    ) {
        Row(modifier) {
            AnimatedVisibility(
                showSubMenu,
                enter = fadeIn(
                    animationSpec = spring(stiffness = Spring.StiffnessHigh)
                ) + expandHorizontally(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessHigh,
                        visibilityThreshold = IntSize.VisibilityThreshold
                    ),
                ),
            ) {
                Crossfade(
                    menuType,
                    animationSpec = tween(200),
                ) { type ->
                    when (type) {
                        SideMenuTab.NONE -> {}

                        SideMenuTab.PAGE_STYLE -> {
                            PageOptions(
                                changeFontFamily,
                                isEditableState,
                                isFavorite,
                                setEditable,
                                fontStyleSelected(),
                                moveToClick,
                                moveToRoot,
                                deleteDocument,
                                toggleFavorite
                            )
                        }

                        SideMenuTab.TEXT_OPTIONS -> {
                            TextOptions(
                                isDarkTheme,
                                selectedMetadataState,
                                boldClick,
                                checkItemClick,
                                listItemClick,
                                codeBlockClick,
                                highLightBlockClick,
                                addImage,
                                addPage,
                                titleClick
                            )
                        }

                        SideMenuTab.EXPORT -> {
                            Actions(
                                exportJson,
                                exportMarkdown,
                            )
                        }

                        SideMenuTab.AI -> {
                            AiOptions(
                                currentModel = currentModel,
                                models = models,
                                askAiBySelection = askAiBySelection,
                                aiSummary = aiSummary,
                                aiActionPoints = aiActionPoints,
                                aiFaq = aiFaq,
                                aiTags = aiTags,
                                selectModel = selectModel
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column(
                modifier = Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.medium
                ).background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.medium)
            ) {
                val spacing = 3.dp

                Spacer(modifier = Modifier.height(spacing))

                val background = @Composable { optionsType: SideMenuTab ->
                    if (optionsType == menuType) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.background
                    }
                }

                val tint = @Composable { optionsType: SideMenuTab ->
                    if (optionsType == menuType) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    }
                }

                Icon(
                    imageVector = WrIcons.pageStyle,
                    contentDescription = "Document Style",
                    modifier = Modifier
                        .padding(horizontal = spacing)
                        .clip(MaterialTheme.shapes.medium)
                        .background(background(SideMenuTab.PAGE_STYLE))
                        .clickable {
                            val menuType = if (menuType != SideMenuTab.PAGE_STYLE) {
                                SideMenuTab.PAGE_STYLE
                            } else {
                                SideMenuTab.NONE
                            }

                            changeSideMenuTab(menuType)
                        }
                        .size(40.dp)
                        .padding(10.dp),
                    tint = tint(SideMenuTab.PAGE_STYLE)
                )

                Spacer(modifier = Modifier.height(1.dp))

                Icon(
                    imageVector = WrIcons.textStyle,
                    contentDescription = "Font Style",
                    modifier = Modifier
                        .padding(horizontal = spacing)
                        .clip(MaterialTheme.shapes.medium)
                        .background(background(SideMenuTab.TEXT_OPTIONS))
                        .clickable {
                            val menuType = if (menuType != SideMenuTab.TEXT_OPTIONS) {
                                SideMenuTab.TEXT_OPTIONS
                            } else {
                                SideMenuTab.NONE
                            }

                            changeSideMenuTab(menuType)
                        }
                        .size(40.dp)
                        .padding(9.dp),
                    tint = tint(SideMenuTab.TEXT_OPTIONS)
                )

                Icon(
                    imageVector = WrIcons.ai,
                    contentDescription = "AI",
                    modifier = Modifier
                        .padding(horizontal = spacing)
                        .clip(MaterialTheme.shapes.medium)
                        .background(background(SideMenuTab.AI))
                        .clickable {
                            val menuType = if (menuType != SideMenuTab.AI) {
                                SideMenuTab.AI
                            } else {
                                SideMenuTab.NONE
                            }

                            changeSideMenuTab(menuType)
                        }
                        .size(40.dp)
                        .padding(9.dp),
                    tint = tint(SideMenuTab.AI)
                )

                Spacer(modifier = Modifier.height(spacing))

                Spacer(modifier = Modifier.height(spacing))

                Icon(
                    imageVector = WrIcons.exportFile,
                    contentDescription = "Export file",
                    modifier = Modifier
                        .padding(horizontal = spacing)
                        .clip(MaterialTheme.shapes.medium)
                        .background(background(SideMenuTab.EXPORT))
                        .clickable {
                            val menuType = if (menuType != SideMenuTab.EXPORT) {
                                SideMenuTab.EXPORT
                            } else {
                                SideMenuTab.NONE
                            }

                            changeSideMenuTab(menuType)
                        }
                        .size(40.dp)
                        .padding(9.dp),
                    tint = tint(SideMenuTab.EXPORT)
                )

                Spacer(modifier = Modifier.height(spacing))
            }
        }
    }
}

@Composable
private fun PageOptions(
    changeFontFamily: (Font) -> Unit,
    isEditableState: StateFlow<Boolean>,
    isFavoriteState: StateFlow<Boolean>,
    setEditable: () -> Unit,
    selectedState: StateFlow<Font>,
    moveButtonClick: () -> Unit,
    moveToRoot: () -> Unit,
    deleteDocument: () -> Unit,
    toggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.shapes.medium
        ).background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.medium)
            .width(250.dp)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)
    ) {
        Title("Font")
        Spacer(modifier = Modifier.height(4.dp))
        FontOptions(changeFontFamily, selectedState)

        Spacer(modifier = Modifier.height(8.dp))

        Title("Actions")
        Spacer(modifier = Modifier.height(6.dp))

        FavoriteButton(isFavorite = isFavoriteState, toggleFavorite)
        Spacer(modifier = Modifier.height(4.dp))

        LockButton(isEditableState, setEditable)
        Spacer(modifier = Modifier.height(4.dp))

        MoveToButton(moveButtonClick)
        Spacer(modifier = Modifier.height(4.dp))

        MoveToHomeButton(moveToRoot)
        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            text = WrStrings.delete(),
            modifier = Modifier.fillMaxWidth(),
            paddingValues = smallButtonPadding(),
            onClick = deleteDocument
        )
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun TitleChanges(
    metadata: Set<SelectionMetadata>,
    modifier: Modifier = Modifier,
    titleClick: (Tag) -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            modifier = Modifier.weight(1F),
            text = WrStrings.title(),
            highlight = metadata.contains(
                SelectionMetadata.TITLE
            ),
            textStyle = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
            onClick = {
                titleClick(Tag.H1)
            }
        )

        TextButton(
            modifier = Modifier.weight(1F),
            text = WrStrings.subtitle(),
            highlight = metadata.contains(
                SelectionMetadata.SUBTITLE
            ),
            textStyle = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
            onClick = {
                titleClick(Tag.H2)
            }
        )

        TextButton(
            modifier = Modifier.weight(1F),
            text = WrStrings.heading(),
            highlight = metadata.contains(
                SelectionMetadata.HEADING
            ),
            textStyle = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
            onClick = {
                titleClick(Tag.H3)
            }
        )
    }
}

@Composable
private fun TextChanges(spanClick: (Span) -> Unit) {
    Row(
        modifier = Modifier.horizontalOptionsRow(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = WrIcons.bold,
            contentDescription = "Bold",
            modifier = Modifier.weight(1F)
                .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                .size(32.dp)
                .clickable { spanClick(Span.BOLD) }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )

        Icon(
            imageVector = WrIcons.italic,
            contentDescription = "Italic",
            modifier = Modifier.weight(1F)
                .size(32.dp)
                .clickable { spanClick(Span.ITALIC) }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )

        Icon(
            imageVector = WrIcons.underline,
            contentDescription = "Underlined text",
            modifier = Modifier.weight(1F)
                .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                .size(32.dp)
                .clickable { spanClick(Span.UNDERLINE) }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun HighlightText(isDarkTheme: Boolean, spanClick: (Span) -> Unit) {
    Row(
        modifier = Modifier.horizontalOptionsRow(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val colors = if (isDarkTheme) {
            listOf(
                Span.HIGHLIGHT_RED to Color(0xFFD32F2F),
                Span.HIGHLIGHT_GREEN to Color(0xFF2E7D32),
                Span.HIGHLIGHT_YELLOW to Color(0xFFF9A825)
            )
        } else {
            listOf(
                Span.HIGHLIGHT_RED to Color(0xFFE57373),
                Span.HIGHLIGHT_GREEN to Color.Green,
                Span.HIGHLIGHT_YELLOW to Color.Yellow
            )
        }

        colors.forEach { (span, color) ->
            Box(
                modifier = Modifier.weight(1F)
                    .size(32.dp)
                    .clickable { spanClick(span) },
            ) {
                Box(
                    modifier = Modifier.size(16.dp)
                        .align(Alignment.Center)
                        .background(color, CircleShape)
                        .clip(CircleShape)
                        .clickable { spanClick(span) }
                )
            }
        }
    }
}

@Composable
private fun IconAndText(text: String, iconImage: ImageVector, click: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(start = 2.dp, end = 2.dp, bottom = 3.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = click)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.medium
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(18.dp),
            imageVector = iconImage,
            contentDescription = "Image",
            tint = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            style = buttonsTextStyle(),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DecorationCommands(commands: Iterable<DecorationButton>) {
    Column {
        commands.inBatches(2)
            .forEach { line ->
                Row {
                    line.forEach { (command, highlight, listener) ->
                        TextButton(
                            modifier = Modifier.weight(1F),
                            text = command,
                            highlight = highlight,
                            onClick = listener
                        )
                    }
                }
            }
    }
}

@Composable
private fun TextButton(
    modifier: Modifier = Modifier,
    text: String,
    highlight: Boolean = false,
    paddingValues: PaddingValues = PaddingValues(
        horizontal = 8.dp,
        vertical = 8.dp
    ),
    textStyle: TextStyle = buttonsTextStyle(),
    fontWeight: FontWeight = FontWeight.Bold,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium

    Text(
        modifier = modifier
            .padding(start = 2.dp, end = 2.dp, bottom = 3.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (highlight) {
                    WriteopiaTheme.colorScheme.optionsSelector
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = shape
            )
            .background(
                color = if (highlight) {
                    WriteopiaTheme.colorScheme.optionsSelector
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = shape
            )
            .padding(paddingValues),
        text = text,
        color = MaterialTheme.colorScheme.onBackground,
        style = textStyle,
        fontWeight = fontWeight,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun InsertCommand(
    selectedMetadataState: StateFlow<Set<SelectionMetadata>>,
    checkItemClick: () -> Unit,
    listItemClick: () -> Unit,
    codeBlockClick: () -> Unit,
) {
    val selectedMetadata by selectedMetadataState.collectAsState()

    Row(modifier = Modifier.horizontalOptionsRow()) {
        val shapeLeft = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)

        Icon(
            imageVector = WrSdkIcons.checkbox,
            contentDescription = "Check box",
            modifier = Modifier.weight(1F)
                .border(
                    width = 1.dp,
                    shape = shapeLeft,
                    color = if (selectedMetadata.contains(SelectionMetadata.CHECK_ITEM)) {
                        WriteopiaTheme.colorScheme.optionsSelector
                    } else {
                        Color.Transparent
                    }
                )
                .background(
                    color = if (selectedMetadata.contains(SelectionMetadata.CHECK_ITEM)) {
                        WriteopiaTheme.colorScheme.optionsSelector
                    } else {
                        Color.Transparent
                    },
                    shape = shapeLeft
                )
                .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                .size(32.dp)
                .clickable(onClick = checkItemClick)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )

        Icon(
            imageVector = WrSdkIcons.list,
            contentDescription = "List item",
            modifier = Modifier.weight(1F)
                .border(
                    width = 1.dp,
                    color = if (selectedMetadata.contains(SelectionMetadata.UNORDERED_LIST_ITEM)) {
                        WriteopiaTheme.colorScheme.optionsSelector
                    } else {
                        Color.Transparent
                    }
                )
                .background(
                    color = if (selectedMetadata.contains(SelectionMetadata.UNORDERED_LIST_ITEM)) {
                        WriteopiaTheme.colorScheme.optionsSelector
                    } else {
                        Color.Transparent
                    },
                )
                .size(32.dp)
                .clickable(onClick = listItemClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )

        val shapeRight = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)

        Icon(
            imageVector = WrIcons.code,
            contentDescription = "Code block",
            modifier = Modifier.weight(1F)
                .border(
                    width = 1.dp,
                    shape = shapeRight,
                    color = if (selectedMetadata.contains(SelectionMetadata.CODE_BLOCK)) {
                        WriteopiaTheme.colorScheme.optionsSelector
                    } else {
                        Color.Transparent
                    }
                )
                .background(
                    color = if (selectedMetadata.contains(SelectionMetadata.CODE_BLOCK)) {
                        WriteopiaTheme.colorScheme.optionsSelector
                    } else {
                        Color.Transparent
                    },
                    shape = shapeRight
                )
                .clip(shapeRight)
                .size(32.dp)
                .clickable(onClick = codeBlockClick)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun Modifier.horizontalOptionsRow() =
    this.fillMaxWidth()
        .background(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.shapes.medium
        )

@Composable
internal fun FontOptions(
    changeFontFamily: (Font) -> Unit,
    selectedState: StateFlow<Font>,
    modifier: Modifier = Modifier,
    selectedColor: Color = WriteopiaTheme.colorScheme.highlight,
    defaultColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val selected by selectedState.collectAsState()

    mapOf(
        Font.SYSTEM.label to FontFamily.Default,
        Font.SERIF.label to FontFamily.Serif,
        Font.MONOSPACE.label to FontFamily.Monospace,
        Font.CURSIVE.label to FontFamily.Cursive,
    ).toList()
        .inBatches(2)
        .forEach { items ->
            Row(modifier) {
                items.forEach { (name, family) ->
                    Text(
                        name,
                        fontFamily = family,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(2.dp)
                            .background(
                                if (selected.label == name) selectedColor else defaultColor,
                                MaterialTheme.shapes.medium
                            ).weight(1F)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                changeFontFamily(Font.fromLabel(name))
                            }
                            .padding(4.dp),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = buttonsTextStyle()
                    )
                }
            }
        }
}

@Composable
private fun TextOptions(
    isDarkTheme: Boolean,
    selectedMetadataState: StateFlow<Set<SelectionMetadata>>,
    spanClick: (Span) -> Unit,
    checkItemClick: () -> Unit,
    listItemClick: () -> Unit,
    codeBlockClick: () -> Unit,
    highLightBlockClick: () -> Unit,
    addImage: (String) -> Unit,
    addPage: () -> Unit,
    titleClick: (Tag) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.shapes.medium
        ).background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.medium)
            .width(250.dp)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)
    ) {
        val selectedMetadata by selectedMetadataState.collectAsState()

        Title("Title")
        Spacer(modifier = Modifier.height(4.dp))
        TitleChanges(metadata = selectedMetadata, titleClick = titleClick)
        Spacer(modifier = Modifier.height(8.dp))

        Title(WrStrings.text())
        Spacer(modifier = Modifier.height(4.dp))
        TextChanges(spanClick)
        Spacer(modifier = Modifier.height(8.dp))

        Title(WrStrings.highlighting())
        Spacer(modifier = Modifier.height(4.dp))
        HighlightText(isDarkTheme, spanClick)
        Spacer(modifier = Modifier.height(8.dp))

        Title(WrStrings.insert())
        Spacer(modifier = Modifier.height(4.dp))
        InsertCommand(selectedMetadataState, checkItemClick, listItemClick, codeBlockClick)
        Spacer(modifier = Modifier.height(8.dp))

        Title(WrStrings.decoration())
        Spacer(modifier = Modifier.height(4.dp))

        DecorationCommands(
            commands = listOf(
                DecorationButton(
                    text = WrStrings.box(),
                    highlight = selectedMetadata.contains(SelectionMetadata.BOX),
                    onClick = highLightBlockClick
                )
//                "Warning" to {},
//                "Tip" to {},
//                "Code" to {}
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        Title(WrStrings.content())
        Spacer(modifier = Modifier.height(4.dp))
        IconAndText(WrStrings.image(), WrIcons.image) {
            fileChooserLoad("")?.let(addImage)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Title(WrStrings.links())
        Spacer(modifier = Modifier.height(4.dp))
        IconAndText(WrStrings.page(), WrSdkIcons.linkPage, addPage)
    }
}

@Composable
private fun Actions(
    exportJson: (String) -> Unit,
    exportMarkdown: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.shapes.medium
        ).background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.medium)
            .width(250.dp)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)
    ) {
        Title(WrStrings.export())

        Spacer(modifier = Modifier.height(4.dp))

        Row {
            TextButton(
                text = WrStrings.json(),
                modifier = Modifier.weight(1F),
                paddingValues = smallButtonPadding()
            ) {
                fileChooserSave()?.let {
                    exportJson(it)
                }
            }

            TextButton(
                text = WrStrings.markdown(),
                modifier = Modifier.weight(1F),
                paddingValues = smallButtonPadding()
            ) {
                fileChooserSave()?.let(exportMarkdown)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun AiOptions(
    currentModel: Flow<String>,
    models: Flow<List<String>>,
    selectModel: (String) -> Unit,
    askAiBySelection: () -> Unit,
    aiSummary: () -> Unit,
    aiActionPoints: () -> Unit,
    aiFaq: () -> Unit,
    aiTags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.shapes.medium
        ).background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.medium)
            .width(250.dp)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)
    ) {
        Title(WrStrings.askAi())

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            text = "Prompt",
            paddingValues = smallButtonPadding(),
            onClick = askAiBySelection
        )

        Spacer(modifier = Modifier.height(2.dp))

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            text = WrStrings.summary(),
            paddingValues = smallButtonPadding(),
            onClick = aiSummary
        )

        Spacer(modifier = Modifier.height(2.dp))

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            text = WrStrings.actionPoints(),
            paddingValues = smallButtonPadding(),
            onClick = aiActionPoints
        )

        Spacer(modifier = Modifier.height(2.dp))

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            text = "FAQ",
            paddingValues = smallButtonPadding(),
            onClick = aiFaq
        )

        Spacer(modifier = Modifier.height(2.dp))

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            text = "Tags",
            paddingValues = smallButtonPadding(),
            onClick = aiTags
        )

        Spacer(modifier = Modifier.height(8.dp))

        val currentModelValue by currentModel.collectAsState(WrStrings.noModelsFound())

        Title(WrStrings.aiModel())

        var showModels by remember {
            mutableStateOf(false)
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = currentModelValue,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(4.dp).clickable {
                showModels = !showModels
            }
        )

        val modelsValue by models.collectAsState(emptyList())

        DropdownMenu(
            expanded = showModels,
            onDismissRequest = { showModels = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            offset = DpOffset(y = 6.dp, x = 6.dp)
        ) {
            modelsValue.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = model,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    },
                    onClick = {
                        selectModel(model)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun buttonsTextStyle() =
    MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)

@Composable
private fun smallButtonPadding() = PaddingValues(horizontal = 8.dp, vertical = 4.dp)

private data class DecorationButton(
    val text: String,
    val highlight: Boolean,
    val onClick: () -> Unit
)
