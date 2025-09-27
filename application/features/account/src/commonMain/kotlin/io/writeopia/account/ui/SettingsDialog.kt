package io.writeopia.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.writeopia.common.utils.download.DownloadState
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.SettingsPanel
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.commonui.buttons.CommonTextButton
import io.writeopia.commonui.workplace.WorkspaceConfigurationDialog
import io.writeopia.model.ColorThemeOption
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

private const val SPACE_AFTER_TITLE = 12
private const val SPACE_AFTER_SUB_TITLE = 6

@Composable
fun SettingsDialog(
    workplacePathState: StateFlow<String>,
    selectedThemePosition: StateFlow<Int>,
    ollamaUrlState: StateFlow<String>,
    ollamaAvailableModels: Flow<ResultData<List<String>>>,
    ollamaSelectedModel: StateFlow<String>,
    downloadModelState: StateFlow<ResultData<DownloadState>>,
    userOnlineState: StateFlow<WriteopiaUser>,
    showDeleteConfirmation: StateFlow<Boolean>,
    syncWorkspaceState: StateFlow<String>,
    onDismissRequest: () -> Unit,
    selectColorTheme: (ColorThemeOption) -> Unit,
    selectWorkplacePath: (String) -> Unit,
    ollamaUrlChange: (String) -> Unit,
    ollamaModelChange: (String) -> Unit,
    ollamaModelsRetry: () -> Unit,
    downloadModel: (String) -> Unit,
    deleteModel: (String) -> Unit,
    signIn: () -> Unit,
    resetPassword: () -> Unit,
    logout: () -> Unit,
    showDeleteConfirm: () -> Unit,
    dismissDeleteConfirm: () -> Unit,
    deleteAccount: () -> Unit,
    syncWorkspace: () -> Unit,
) {
    val ollamaUrl by ollamaUrlState.collectAsState()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .width(700.dp)
                .fillMaxHeight(fraction = 0.7F),
//                .padding(horizontal = 40.dp, vertical = 20.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            SettingsPanel(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                accountScreen = {
                    AccountScreen(
                        userOnlineState,
                        showDeleteConfirmation,
                        signIn,
                        resetPassword,
                        logout,
                        dismissDeleteConfirm,
                        showDeleteConfirm,
                        deleteAccount
                    )
                },
                appearanceScreen = {
                    ColorThemeOptions(
                        selectedThemePosition = selectedThemePosition,
                        selectColorTheme = selectColorTheme
                    )
                },
                directoryScreen = {
                    WorkspaceSection(
                        workplacePathState = workplacePathState,
                        showPath = true,
                        selectWorkplacePath = selectWorkplacePath,
                        syncWorkspace = syncWorkspace,
                        syncWorkspaceState = syncWorkspaceState
                    )
                },
                aiScreen = {
                    AiSection(
                        ollamaUrl,
                        ollamaAvailableModels,
                        ollamaSelectedModel,
                        downloadModelState,
                        ollamaUrlChange,
                        ollamaModelChange,
                        ollamaModelsRetry,
                        downloadModel,
                        deleteModel
                    )
                }
            )
        }
    }
}

@Composable
fun SettingsScreen(
    showPath: Boolean = true,
    showOllamaConfig: Boolean,
    selectedThemePosition: StateFlow<Int>,
    workplacePathState: StateFlow<String>,
    syncWorkspaceState: StateFlow<String>,
    ollamaUrl: String,
    ollamaAvailableModels: Flow<ResultData<List<String>>>,
    ollamaSelectedModel: StateFlow<String>,
    downloadModelState: StateFlow<ResultData<DownloadState>>,
    selectColorTheme: (ColorThemeOption) -> Unit,
    selectWorkplacePath: (String) -> Unit,
    ollamaUrlChange: (String) -> Unit,
    ollamaModelChange: (String) -> Unit,
    ollamaModelsRetry: () -> Unit,
    downloadModel: (String) -> Unit,
    deleteModel: (String) -> Unit,
    syncWorkspace: () -> Unit,
) {
    ColorThemeOptions(
        selectedThemePosition = selectedThemePosition,
        selectColorTheme = selectColorTheme
    )

    Spacer(modifier = Modifier.height(20.dp))

    WorkspaceSection(
        workplacePathState,
        syncWorkspaceState,
        showPath,
        selectWorkplacePath,
        syncWorkspace
    )

    Spacer(modifier = Modifier.height(20.dp))

    if (showOllamaConfig) {
        AiSection(
            ollamaUrl = ollamaUrl,
            ollamaAvailableModels,
            ollamaSelectedModel,
            downloadModelState,
            ollamaUrlChange,
            ollamaModelChange,
            ollamaModelsRetry,
            downloadModel,
            deleteModel
        )
    }

    Spacer(modifier = Modifier.height(30.dp))
}

@Composable
private fun AccountScreen(
    userOnlineState: StateFlow<WriteopiaUser>,
    showDeleteConfirmation: StateFlow<Boolean>,
    signIn: () -> Unit,
    resetPassword: () -> Unit,
    logout: () -> Unit,
    dismissDeleteConfirm: () -> Unit,
    showDeleteConfirm: () -> Unit,
    deleteAccount: () -> Unit,
) {
    Column {
        val titleStyle = MaterialTheme.typography.titleLarge
        val titleColor = MaterialTheme.colorScheme.onBackground

        Text(WrStrings.account(), style = titleStyle, color = titleColor)
        Spacer(modifier = Modifier.height(SPACE_AFTER_TITLE.dp))

        val userOnline by userOnlineState.collectAsState()

        if (userOnline.id != WriteopiaUser.DISCONNECTED) {
            Text(
                "${WrStrings.account()}: ${userOnline.name} - ${userOnline.tier.tierName()}",
                style = MaterialTheme.typography.bodySmall,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth()
            )

            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                CommonTextButton(
                    text = WrStrings.changeAccount(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    signIn()
                }

                Spacer(modifier = Modifier.height(4.dp))

                CommonTextButton(
                    text = WrStrings.resetPassword(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    resetPassword()
                }

                Spacer(modifier = Modifier.height(4.dp))

                CommonTextButton(text = WrStrings.logout(), modifier = Modifier.fillMaxWidth()) {
                    logout()
                }

                Spacer(modifier = Modifier.height(4.dp))

                val showDelete by showDeleteConfirmation.collectAsState()

                CommonTextButton(
                    text = WrStrings.deleteAccount(),
                    modifier = Modifier.fillMaxWidth(),
                    defaultColor = Color.Red,
                    textColor = MaterialTheme.colorScheme.onPrimary,
                    clickListener = showDeleteConfirm
                )

                if (showDelete) {
                    Dialog(onDismissRequest = dismissDeleteConfirm) {
                        Card(modifier = Modifier, shape = MaterialTheme.shapes.large) {
                            Column(
                                modifier = Modifier.padding(
                                    start = 30.dp,
                                    end = 30.dp,
                                    bottom = 20.dp,
                                    top = 20.dp
                                ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    WrStrings.areYouSure(),
                                    style = MaterialTheme.typography.displaySmall
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    WrStrings.notesWillBeDeleted(),
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(WrStrings.confirmEmail())

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    "",
                                    onValueChange = {},
                                    singleLine = true,
                                    placeholder = {
                                        Text(WrStrings.email())
                                    }
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    TextButton(
                                        onClick = dismissDeleteConfirm,
                                        modifier = Modifier.padding(8.dp),
                                    ) {
                                        Text(WrStrings.dismiss())
                                    }

                                    TextButton(
                                        onClick = {
                                            deleteAccount()
                                        },
                                        modifier = Modifier.padding(8.dp),
                                    ) {
                                        Text(WrStrings.confirm())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                WrStrings.youAreOffline(),
                style = MaterialTheme.typography.bodySmall,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth()
            )

            CommonButton(text = WrStrings.singIn()) {
                signIn()
            }
        }
    }
}

@Composable
private fun WorkspaceSection(
    workplacePathState: StateFlow<String>,
    syncWorkspaceState: StateFlow<String>,
    showPath: Boolean = true,
    selectWorkplacePath: (String) -> Unit,
    syncWorkspace: () -> Unit,
) {
    Column {
        val titleStyle = MaterialTheme.typography.titleLarge
        val titleColor = MaterialTheme.colorScheme.onBackground

        val workplacePath by workplacePathState.collectAsState()
        var showEditPathDialog by remember {
            mutableStateOf(false)
        }

        if (showPath) {
            Text(WrStrings.localFolder(), style = titleStyle, color = titleColor)

            Spacer(modifier = Modifier.height(SPACE_AFTER_TITLE.dp))

            val textShape = MaterialTheme.shapes.medium

            Text(
                workplacePath,
                style = MaterialTheme.typography.bodySmall,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    textShape
                )
                    .clip(shape = textShape)
                    .clickable {
                        showEditPathDialog = true
                    }
                    .padding(8.dp)
                    .fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(SPACE_AFTER_TITLE.dp))

        Text(text = "Sync", style = titleStyle, color = titleColor)

        Spacer(modifier = Modifier.height(SPACE_AFTER_TITLE.dp))

        val lastSync by syncWorkspaceState.collectAsState()

        CommonButton(
            text = "Sync workspace",
            clickListener = syncWorkspace
        )

        if (lastSync.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = lastSync,
                style = MaterialTheme.typography.bodyMedium,
                color = titleColor
            )
        }

        if (showEditPathDialog) {
            WorkspaceConfigurationDialog(
                currentPath = workplacePath,
                pathChange = selectWorkplacePath,
                onDismissRequest = {
                    showEditPathDialog = false
                },
                onConfirmation = {
                    showEditPathDialog = false
                },
            )
        }
    }
}

@Composable
private fun AiSection(
    ollamaUrl: String,
    ollamaAvailableModels: Flow<ResultData<List<String>>>,
    ollamaSelectedModel: StateFlow<String>,
    downloadModelState: StateFlow<ResultData<DownloadState>>,
    ollamaUrlChange: (String) -> Unit,
    ollamaModelChange: (String) -> Unit,
    ollamaModelsRetry: () -> Unit,
    downloadModel: (String) -> Unit,
    deleteModel: (String) -> Unit,
) {
    Column {
        val titleStyle = MaterialTheme.typography.titleLarge
        val titleColor = MaterialTheme.colorScheme.onBackground

        Text(WrStrings.ollama(), style = titleStyle, color = titleColor)

        Spacer(modifier = Modifier.height(SPACE_AFTER_TITLE.dp))

        Text(WrStrings.url(), style = MaterialTheme.typography.bodyMedium, color = titleColor)

        Spacer(modifier = Modifier.height(SPACE_AFTER_SUB_TITLE.dp))

        BasicTextField(
            modifier = Modifier.border(
                1.dp,
                MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.shapes.medium
            ).padding(10.dp)
                .fillMaxWidth(),
            value = ollamaUrl,
            onValueChange = ollamaUrlChange,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            WrStrings.availableModels(),
            style = MaterialTheme.typography.bodyMedium,
            color = titleColor
        )

        SelectModels(
            ollamaAvailableModels,
            ollamaSelectedModel,
            ollamaModelChange,
            ollamaModelsRetry,
            deleteModel
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            WrStrings.downloadModels(),
            style = MaterialTheme.typography.bodyMedium,
            color = titleColor
        )

        DownloadModels(downloadModelState, downloadModel)
    }
}

@Composable
private fun SelectModels(
    ollamaAvailableModels: Flow<ResultData<List<String>>>,
    ollamaSelectedModel: StateFlow<String>,
    ollamaModelChange: (String) -> Unit,
    ollamaModelsRetry: () -> Unit,
    deleteModel: (String) -> Unit,
) {
    val modelsResult = ollamaAvailableModels.collectAsState(ResultData.Idle()).value
    val ollamaSelected by ollamaSelectedModel.collectAsState()

    Spacer(modifier = Modifier.height(SPACE_AFTER_SUB_TITLE.dp))

    when (modelsResult) {
        is ResultData.Complete -> {
            modelsResult.data.forEachIndexed { i, model ->

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(MaterialTheme.shapes.large)
                        .clickable {
                            ollamaModelChange(model)
                        }
                        .let { modifierLet ->
                            if (model == ollamaSelected) {
                                modifierLet.background(WriteopiaTheme.colorScheme.highlight)
                            } else {
                                modifierLet
                            }
                        }
                        .padding(8.dp)
                ) {
                    Text(
                        modifier = Modifier.weight(1F),
                        text = model,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )

                    if (!model.startsWith("No models")) {
                        Spacer(modifier = Modifier.width(6.dp))

                        Icon(
                            modifier = Modifier.clip(CircleShape)
                                .clickable {
                                    deleteModel(model)
                                }
                                .padding(4.dp)
                                .size(20.dp),
                            imageVector = WrIcons.delete,
                            contentDescription = "Trash can",
                            tint = Color.Red
                        )
                    }
                }

                if (i != modelsResult.data.lastIndex) {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }

        is ResultData.Error -> {
            val errorText = buildAnnotatedString {
                append(WrStrings.errorRequestingModels())
                withLink(LinkAnnotation.Url("https://ollama.com")) {
                    withStyle(style = SpanStyle(color = WriteopiaTheme.colorScheme.linkColor)) {
                        append("https://ollama.com")
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .padding(8.dp)
                        .weight(1F),
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Text(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClick = ollamaModelsRetry)
                        .background(
                            WriteopiaTheme.colorScheme.highlight,
                            MaterialTheme.shapes.medium
                        )
                        .padding(4.dp),
                    text = WrStrings.retry(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        is ResultData.Idle -> {}
        is ResultData.Loading, is ResultData.InProgress -> {
            CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadModels(
    downloadModelState: StateFlow<ResultData<DownloadState>>,
    downloadModel: (String) -> Unit,
) {
    Spacer(modifier = Modifier.height(SPACE_AFTER_SUB_TITLE.dp))

    var modelToDownload by remember {
        mutableStateOf("")
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = WrStrings.suggestions(), style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.width(6.dp))

        listOf("deepseek-r1:7b", "llama3.2").forEach { model ->
            Text(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        downloadModel(model)
                    }
                    .padding(8.dp),
                text = model,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        val interactionSource = remember { MutableInteractionSource() }

        BasicTextField(
            modifier = Modifier.border(
                1.dp,
                MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.shapes.medium
            )
                .padding(10.dp)
                .weight(1F),
            value = modelToDownload,
            onValueChange = { value ->
                modelToDownload = value
            },
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            decorationBox = @Composable { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = modelToDownload,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = false,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    placeholder = {
                        Text(
                            text = WrStrings.writeYourAiModel(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    },
                    colors = transparentTextInputColors(),
                    contentPadding = PaddingValues(0.dp)
                )
            }
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            modifier = Modifier.size(34.dp)
                .clip(CircleShape)
                .clickable {
                    downloadModel(modelToDownload)
                }.padding(6.dp),
            imageVector = WrIcons.download,
            contentDescription = WrStrings.downloadModel(),
            //            stringResource(R.string.note_list),
            tint = MaterialTheme.colorScheme.onBackground
        )
    }

    when (val downloadState = downloadModelState.collectAsState().value) {
        is ResultData.Complete -> {}
        is ResultData.Error -> {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "${WrStrings.errorModelDownload()} ${downloadState.exception?.message}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        is ResultData.Idle -> {}
        is ResultData.InProgress -> {
            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val download = downloadState.data
                Text(
                    download.title,
                    modifier = Modifier.weight(1F),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    download.info,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { downloadState.data.percentage },
                modifier = Modifier.fillMaxWidth()
            )
        }

        is ResultData.Loading -> {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun ColorThemeOptions(
    selectedThemePosition: StateFlow<Int>,
    selectColorTheme: (ColorThemeOption) -> Unit
) {
    val titleStyle = MaterialTheme.typography.titleLarge
    val titleColor = MaterialTheme.colorScheme.onBackground

    Column {
        Text(WrStrings.colorTheme(), style = titleStyle, color = titleColor)

        Spacer(modifier = Modifier.height(SPACE_AFTER_TITLE.dp))

        val spaceWidth = 10.dp

        Row(modifier = Modifier.fillMaxWidth().height(70.dp)) {
            Option(
                text = WrStrings.lightTheme(),
                imageVector = WrIcons.colorModeLight,
                contextDescription = WrStrings.lightTheme(),
                selectColorTheme = {
                    selectColorTheme(ColorThemeOption.LIGHT)
                }
            )

            Spacer(modifier = Modifier.width(spaceWidth))

            Option(
                text = WrStrings.darkTheme(),
                imageVector = WrIcons.colorModeDark,
                contextDescription = WrStrings.darkTheme(),
                selectColorTheme = {
                    selectColorTheme(ColorThemeOption.DARK)
                }
            )

            Spacer(modifier = Modifier.width(spaceWidth))

            Option(
                text = WrStrings.systemTheme(),
                imageVector = WrIcons.colorModeSystem,
                contextDescription = WrStrings.systemTheme(),
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
    selectColorTheme: (ColorThemeOption) -> Unit
) {
    val typography = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
    val color = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = Modifier
            .orderConfigModifierHorizontal {
                selectColorTheme(ColorThemeOption.SYSTEM)
            }
            .fillMaxHeight()
            .weight(1F)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = imageVector,
                contentDescription = contextDescription,
                //            stringResource(R.string.note_list),
                tint = MaterialTheme.colorScheme.onPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(text, style = typography, color = color)
        }
    }
}

@Composable
private fun transparentTextInputColors() =
    TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary
    )
