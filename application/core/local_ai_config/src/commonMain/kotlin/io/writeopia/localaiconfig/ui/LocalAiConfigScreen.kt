package io.writeopia.localaiconfig.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.download.DownloadState
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.controller.LocalAiConfigController
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.LocalAiWizardDialog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

private const val SPACE_AFTER_TITLE = 12
private const val SPACE_AFTER_SUB_TITLE = 6

/**
 * Local AI configuration UI (auto-configure wizard + manual URL/model configuration) shared by
 * the desktop Settings screen and the offline space's first-run setup - both feed it a
 * [LocalAiConfigController], which may be backed by different implementations (a session-bound
 * one in Settings, a lightweight session-independent one for the offline first-run flow).
 *
 * [onDownloadStarted] is called once a model picked in the wizard starts downloading, in the
 * background as an AI task.
 */
@Composable
fun LocalAiConfigScreen(
    controller: LocalAiConfigController,
    modifier: Modifier = Modifier,
    onDownloadStarted: () -> Unit = {},
) {
    Column(modifier = modifier) {
        val titleStyle = MaterialTheme.typography.titleLarge
        val titleColor = MaterialTheme.colorScheme.onBackground

        Text(WrStrings.localAi(), style = titleStyle, color = titleColor)

        Spacer(modifier = Modifier.height(SPACE_AFTER_TITLE.dp))

        // Configuration status indicator
        val availableModelsState by controller.modelsForUrl.collectAsState()
        val selectedModel by controller.localAiSelectedModelState.collectAsState()

        // AI is considered configured if we can successfully fetch models and a model is selected
        val isConfigured = availableModelsState is ResultData.Complete &&
            (availableModelsState as? ResultData.Complete)?.data?.isNotEmpty() == true &&
            selectedModel.isNotBlank()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                imageVector = if (isConfigured) WrIcons.check else WrIcons.close,
                contentDescription = null,
                tint = if (isConfigured) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isConfigured) WrStrings.aiConfigured() else WrStrings.aiNotConfigured(),
                style = MaterialTheme.typography.bodySmall,
                color = if (isConfigured) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }

        // Wizard trigger button
        CommonButton(
            text = WrStrings.autoConfigureLocalAi(),
            clickListener = controller::openWizard
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Collapsible Manual Configuration Section
        var manualConfigExpanded by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable { manualConfigExpanded = !manualConfigExpanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                WrStrings.manualConfiguration(),
                style = MaterialTheme.typography.bodyMedium,
                color = titleColor,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (manualConfigExpanded) WrIcons.smallArrowUp else WrIcons.smallArrowDown,
                contentDescription = if (manualConfigExpanded) "Collapse" else "Expand",
                tint = titleColor
            )
        }

        AnimatedVisibility(visible = manualConfigExpanded) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))

                Text(WrStrings.url(), style = MaterialTheme.typography.bodyMedium, color = titleColor)

                Spacer(modifier = Modifier.height(SPACE_AFTER_SUB_TITLE.dp))

                val localAiUrl by controller.localAiUrl.collectAsState()

                BasicTextField(
                    modifier = Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.shapes.medium
                    ).padding(10.dp)
                        .fillMaxWidth(),
                    value = localAiUrl,
                    onValueChange = controller::changeLocalAiUrl,
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
                    controller.modelsForUrl,
                    controller.localAiSelectedModelState,
                    controller::selectLocalAiModel,
                    controller::retryModels,
                    controller::deleteModel
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    WrStrings.downloadModels(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = titleColor
                )

                DownloadModels(controller.downloadModelState, controller::modelToDownload)
            }
        }
    }

    LocalAiWizardDialog(
        wizardState = controller.wizardState,
        onClose = controller::closeWizard,
        onSelectProviderAndModel = { providerUrl, modelName ->
            controller.selectProviderAndModel(providerUrl, modelName, onDownloadStarted)
        },
        onRetry = controller::openWizard
    )
}

@Composable
private fun SelectModels(
    localAiAvailableModels: Flow<ResultData<List<String>>>,
    localAiSelectedModel: StateFlow<String>,
    localAiModelChange: (String) -> Unit,
    localAiModelsRetry: () -> Unit,
    deleteModel: (String) -> Unit,
) {
    val modelsResult = localAiAvailableModels.collectAsState(ResultData.Idle()).value
    val localAiSelected by localAiSelectedModel.collectAsState()

    Spacer(modifier = Modifier.height(SPACE_AFTER_SUB_TITLE.dp))

    when (modelsResult) {
        is ResultData.Complete -> {
            modelsResult.data.forEachIndexed { i, model ->

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(MaterialTheme.shapes.large)
                        .clickable {
                            localAiModelChange(model)
                        }
                        .let { modifierLet ->
                            if (model == localAiSelected) {
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
                        .clickable(onClick = localAiModelsRetry)
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
private fun transparentTextInputColors() =
    TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary
    )
