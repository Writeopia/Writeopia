package io.writeopia.account.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.writeopia.common.utils.configuration.LocalPlatform
import io.writeopia.common.utils.configuration.PlatformType
import io.writeopia.common.utils.date.formatCompactNumber
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.SettingsPanel
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.commonui.workplace.WorkspacePathSelector
import io.writeopia.controller.LocalAiConfigController
import io.writeopia.model.AiProvider
import io.writeopia.localaiconfig.ui.LocalAiConfigScreen
import io.writeopia.model.AccentColor
import io.writeopia.model.ColorThemeOption
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

private const val SPACE_AFTER_TITLE = 12
private const val SPACE_AFTER_SUB_TITLE = 6

@Composable
fun SettingsDialog(
    workplacePathState: StateFlow<String>,
    selectedColorTheme: StateFlow<ColorThemeOption?>,
    selectedAccentColor: StateFlow<AccentColor?>,
    localAiConfigController: LocalAiConfigController,
    aiProviderState: StateFlow<AiProvider>,
    aiProviderChoices: StateFlow<List<AiProvider>>,
    isCloudAiAvailable: StateFlow<Boolean>,
    selectAiProvider: (AiProvider) -> Unit,
    cloudAiUsageState: StateFlow<CloudAiUsageState>,
    userOnlineState: StateFlow<WriteopiaUser>,
    showDeleteConfirmation: StateFlow<Boolean>,
    workspaces: StateFlow<ResultData<List<Workspace>>>,
    workspaceToEdit: Flow<Workspace?>,
    logoutInProgress: StateFlow<Boolean>,
    deleteAccountInProgress: StateFlow<Boolean>,
    onDismissRequest: () -> Unit,
    selectColorTheme: (ColorThemeOption) -> Unit,
    selectAccentColor: (AccentColor) -> Unit,
    selectWorkplacePath: (String) -> Unit,
    loadCloudAiUsage: () -> Unit,
    signIn: () -> Unit,
    switchSpace: () -> Unit,
    changeWorkspace: () -> Unit,
    resetPassword: () -> Unit,
    logout: () -> Unit,
    showDeleteConfirm: () -> Unit,
    dismissDeleteConfirm: () -> Unit,
    deleteAccount: () -> Unit,
    addUserToTeam: (String) -> Unit,
    selectWorkspaceToManage: (String) -> Unit,
    usersInSelectedWorkspace: Flow<ResultData<List<String>>>,
    exportWorkspaceState: StateFlow<ResultData<Unit>>,
    onExportWorkspace: (String) -> Unit,
    onResetExportState: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val currentPlatform = LocalPlatform.current

        Card(
            modifier = Modifier
                .width(700.dp)
                .fillMaxHeight(fraction = if (currentPlatform.isDesktop()) 0.7F else 1F),
//                .padding(horizontal = 40.dp, vertical = 20.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            SettingsPanel(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                accountScreen = {
                    AccountScreen(
                        userOnlineState = userOnlineState,
                        workspaces = workspaces,
                        showDeleteConfirmation = showDeleteConfirmation,
                        exportWorkspaceState = exportWorkspaceState,
                        logoutInProgress = logoutInProgress,
                        deleteAccountInProgress = deleteAccountInProgress,
                        signIn = signIn,
                        switchSpace = switchSpace,
                        changeWorkspace = changeWorkspace,
                        resetPassword = resetPassword,
                        logout = logout,
                        dismissDeleteConfirm = dismissDeleteConfirm,
                        showDeleteConfirm = showDeleteConfirm,
                        deleteAccount = deleteAccount,
                        onExportWorkspace = onExportWorkspace,
                        onResetExportState = onResetExportState,
                    )
                },
                appearanceScreen = {
                    Column {
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
                },
                directoryScreen = {
                    WorkspaceSection(
                        workplacePathState = workplacePathState,
                        showPath = true,
                        selectWorkplacePath = selectWorkplacePath,
                    )
                },
                aiScreen = {
                    AiSection(
                        aiProviderState = aiProviderState,
                        aiProviderChoices = aiProviderChoices,
                        isCloudAiAvailable = isCloudAiAvailable,
                        selectAiProvider = selectAiProvider,
                        cloudAiUsageState = cloudAiUsageState,
                        loadCloudAiUsage = loadCloudAiUsage,
                        localAiConfigController = localAiConfigController,
                    )
                },
                teamsScreen = {
                    TeamsSection(
                        workspaces,
                        workspaceToEdit,
                        selectWorkspaceToManage,
                        addUserToTeam,
                        usersInSelectedWorkspace
                    )
                }
            )
        }
    }
}

@Composable
private fun AccountScreen(
    userOnlineState: StateFlow<WriteopiaUser>,
    workspaces: StateFlow<ResultData<List<Workspace>>>,
    showDeleteConfirmation: StateFlow<Boolean>,
    exportWorkspaceState: StateFlow<ResultData<Unit>>,
    logoutInProgress: StateFlow<Boolean>,
    deleteAccountInProgress: StateFlow<Boolean>,
    signIn: () -> Unit,
    switchSpace: () -> Unit,
    changeWorkspace: () -> Unit,
    resetPassword: () -> Unit,
    logout: () -> Unit,
    dismissDeleteConfirm: () -> Unit,
    showDeleteConfirm: () -> Unit,
    deleteAccount: () -> Unit,
    onExportWorkspace: (String) -> Unit,
    onResetExportState: () -> Unit,
) {
    Column {
        val titleStyle = MaterialTheme.typography.titleLarge
        val titleColor = MaterialTheme.colorScheme.onBackground

        val userOnline by userOnlineState.collectAsState()

        if (userOnline.isOnline) {
            ChooseTeam(workspaces)
            Spacer(modifier = Modifier.height(SPACE_AFTER_TITLE.dp))

            Text(WrStrings.account(), style = titleStyle, color = titleColor)

            Text(
                "${WrStrings.account()}: ${userOnline.name} - ${userOnline.tier.tierName()}",
                style = MaterialTheme.typography.bodySmall,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(SPACE_AFTER_SUB_TITLE.dp))

            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                CommonButton(
                    text = WrStrings.changeWorkspace(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    changeWorkspace()
                }

                Spacer(modifier = Modifier.height(4.dp))

                CommonButton(
                    text = WrStrings.resetPassword(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    resetPassword()
                }

                Spacer(modifier = Modifier.height(4.dp))

                CommonButton(text = WrStrings.logout(), modifier = Modifier.fillMaxWidth()) {
                    logout()
                }
            }

            // Signing out dialog
            val isLoggingOut by logoutInProgress.collectAsState()
            if (isLoggingOut) {
                SigningOutDialog()
            }

            // Export section
            var showExportDialog by remember { mutableStateOf(false) }

            Spacer(modifier = Modifier.height(SPACE_AFTER_TITLE.dp))

            Text(WrStrings.export(), style = titleStyle, color = titleColor)

            Spacer(modifier = Modifier.height(SPACE_AFTER_SUB_TITLE.dp))

            Text(
                WrStrings.exportWorkspaceDescription(),
                style = MaterialTheme.typography.bodySmall,
                color = titleColor
            )

            Spacer(modifier = Modifier.height(SPACE_AFTER_SUB_TITLE.dp))

            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                CommonButton(
                    text = WrStrings.exportWorkspace(),
                    modifier = Modifier.fillMaxWidth(),
                    clickListener = { showExportDialog = true }
                )
            }

            if (showExportDialog) {
                ExportWorkspaceDialog(
                    workspacesState = workspaces,
                    exportState = exportWorkspaceState,
                    onExport = onExportWorkspace,
                    onDismiss = {
                        showExportDialog = false
                        onResetExportState()
                    }
                )
            }

            // Danger zone section
            Spacer(modifier = Modifier.height(SPACE_AFTER_TITLE.dp))

            Text("Danger zone", style = titleStyle, color = titleColor)

            Spacer(modifier = Modifier.height(SPACE_AFTER_SUB_TITLE.dp))

            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                val showDelete by showDeleteConfirmation.collectAsState()

                Spacer(modifier = Modifier.height(SPACE_AFTER_SUB_TITLE.dp))

                CommonButton(
                    text = WrStrings.deleteAccount(),
                    modifier = Modifier.fillMaxWidth(),
                    defaultColor = Color.Red,
                    textStyle =  MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onPrimary
                    ),
                    clickListener = showDeleteConfirm
                )

                if (showDelete) {
                    val isDeletingAccount by deleteAccountInProgress.collectAsState()

                    Dialog(
                        onDismissRequest = dismissDeleteConfirm,
                        properties = DialogProperties(
                            dismissOnBackPress = !isDeletingAccount,
                            dismissOnClickOutside = !isDeletingAccount
                        )
                    ) {
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

                                var confirmEmailInput by remember { mutableStateOf("") }

                                OutlinedTextField(
                                    confirmEmailInput,
                                    onValueChange = { confirmEmailInput = it },
                                    singleLine = true,
                                    enabled = !isDeletingAccount,
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
                                        enabled = !isDeletingAccount,
                                        modifier = Modifier.padding(8.dp),
                                    ) {
                                        Text(WrStrings.dismiss())
                                    }

                                    TextButton(
                                        onClick = {
                                            deleteAccount()
                                        },
                                        enabled = !isDeletingAccount &&
                                            confirmEmailInput.trim()
                                                .equals(userOnline.email, ignoreCase = true),
                                        modifier = Modifier.padding(8.dp),
                                    ) {
                                        if (isDeletingAccount) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text(WrStrings.confirm())
                                        }
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

            if (LocalPlatform.current != PlatformType.WEB) {
                Spacer(modifier = Modifier.height(8.dp))

                CommonButton(text = WrStrings.switchSpace()) {
                    switchSpace()
                }
            }
        }
    }
}

@Composable
private fun ChooseTeam(workspacesState: StateFlow<ResultData<List<Workspace>>>) {
    val titleStyle = MaterialTheme.typography.titleLarge
    val titleColor = MaterialTheme.colorScheme.onBackground

    val workspaces by workspacesState.collectAsState()

    if (workspaces is ResultData.Complete &&
        (workspaces as ResultData.Complete<List<Workspace>>).data.isNotEmpty()
    ) {
        Text(WrStrings.yourTeams(), style = titleStyle, color = titleColor)

        Column(
            modifier = Modifier.width(240.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            (workspaces as ResultData.Complete<List<Workspace>>).data.forEach { workspace ->
                TeamLine(workspace)
            }
        }
    }
}

@Composable
private fun TeamLine(workspace: Workspace, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        BasicText(
            workspace.name,
            style = MaterialTheme.typography
                .bodySmall
                .copy(MaterialTheme.colorScheme.onBackground)
        )

        Spacer(modifier = Modifier.weight(1F))

        BasicText(
            workspace.role,
            style = MaterialTheme.typography
                .bodySmall
                .copy(MaterialTheme.colorScheme.onBackground)
        )
    }
}

@Composable
private fun WorkspaceSection(
    workplacePathState: StateFlow<String>,
    showPath: Boolean = true,
    selectWorkplacePath: (String) -> Unit,
) {
    Column {
        val workplacePath by workplacePathState.collectAsState()

        if (showPath) {
            Text(
                WrStrings.localFolder(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                WrStrings.localFolderDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(SPACE_AFTER_TITLE.dp))

            WorkspacePathSelector(
                workplacePath = workplacePath,
                selectWorkplacePath = selectWorkplacePath
            )
        }
    }
}

@Composable
private fun AiSection(
    aiProviderState: StateFlow<AiProvider>,
    aiProviderChoices: StateFlow<List<AiProvider>>,
    isCloudAiAvailable: StateFlow<Boolean>,
    selectAiProvider: (AiProvider) -> Unit,
    cloudAiUsageState: StateFlow<CloudAiUsageState>,
    loadCloudAiUsage: () -> Unit,
    localAiConfigController: LocalAiConfigController,
) {
    Column {
        val titleStyle = MaterialTheme.typography.titleLarge
        val titleColor = MaterialTheme.colorScheme.onBackground
        val cloudAiAvailable by isCloudAiAvailable.collectAsState()

        // Who answers the AI commands, like the "Run AI with" picker of the Mac app
        AiProviderOptions(
            selectedProviderState = aiProviderState,
            choicesState = aiProviderChoices,
            localAiModelState = localAiConfigController.localAiSelectedModelState,
            isOnline = cloudAiAvailable,
            selectProvider = selectAiProvider
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Cloud AI usage: only with a session, offline the endpoint can't answer
        if (cloudAiAvailable) {
            Text(WrStrings.cloudAi(), style = titleStyle, color = titleColor)

            Spacer(modifier = Modifier.height(SPACE_AFTER_TITLE.dp))

            CloudAiUsageSection(cloudAiUsageState, loadCloudAiUsage)

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Local AI Section - shared with the offline space's first-run setup screen
        LocalAiConfigScreen(controller = localAiConfigController)
    }
}

@Composable
private fun CloudAiUsageSection(
    cloudAiUsageState: StateFlow<CloudAiUsageState>,
    loadCloudAiUsage: () -> Unit
) {
    val usageState by cloudAiUsageState.collectAsState()

    // Load usage when the section is first displayed
    androidx.compose.runtime.LaunchedEffect(Unit) {
        loadCloudAiUsage()
    }

    when (val state = usageState) {
        is CloudAiUsageState.Loading -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    WrStrings.loadingUsage(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        is CloudAiUsageState.Error -> {
            Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        is CloudAiUsageState.Success -> {
            val usage = state.usage
            val usedTokens = usage.totalTokens
            val quotaTokens = usage.quota
            val usageProgress = if (quotaTokens > 0) {
                (usedTokens.toFloat() / quotaTokens.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            Column {
                // Token count
                Text(
                    "${formatCompactNumber(usedTokens)} / ${formatCompactNumber(quotaTokens)}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    WrStrings.tokensUsed(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = { usageProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (usageProgress > 0.9f) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    "${(usageProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )

                if (usage.requestCount == 0L) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        WrStrings.noUsageData(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamsSection(
    workspacesState: StateFlow<ResultData<List<Workspace>>>,
    selectedWorkspaceState: Flow<Workspace?>,
    selectWorkspace: (String) -> Unit,
    addUserToTeam: (String) -> Unit,
    usersInSelectedWorkspace: Flow<ResultData<List<String>>>
) {
    Column {
        val titleStyle = MaterialTheme.typography.titleLarge
        val titleColor = MaterialTheme.colorScheme.onBackground

        Text(WrStrings.manageTeams(), style = titleStyle, color = titleColor)

        Spacer(modifier = Modifier.height(SPACE_AFTER_TITLE.dp))

        val workspaces by workspacesState.collectAsState()

        when (workspaces) {
            is ResultData.Complete -> {
                val workspaces = (workspaces as ResultData.Complete<List<Workspace>>).data

                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(workspaces) { workspace ->
                        CommonButton(text = workspace.name) {
                            selectWorkspace(workspace.id)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val selected = selectedWorkspaceState.collectAsState(null).value

                AnimatedVisibility(selected != null) {
                    if (selected != null) {
                        Column {
                            AddUserToWorkspace(selected, usersInSelectedWorkspace, addUserToTeam)
                        }
                    }
                }
            }

            is ResultData.Error -> {
                BasicText(
                    text = WrStrings.errorLoadingTeams(),
                    style = MaterialTheme.typography.bodySmall.copy(color = titleColor)
                )
            }

            is ResultData.Idle -> {
                BasicText(
                    text = WrStrings.youAreOffline(),
                    style = MaterialTheme.typography.bodySmall.copy(color = titleColor)
                )
            }

            is ResultData.InProgress -> {
                CircularProgressIndicator()
            }

            is ResultData.Loading -> {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun AddUserToWorkspace(
    selected: Workspace,
    usersInSelectedWorkspace: Flow<ResultData<List<String>>>,
    addUserToTeam: (String) -> Unit
) {
    if (selected.role == "ADMIN") {
        BasicText(
            text = "${WrStrings.addToTeam()} ${selected.name}",
            style = MaterialTheme
                .typography
                .titleSmall
                .copy(color = MaterialTheme.colorScheme.onBackground)
        )

        var userEmail by remember {
            mutableStateOf("")
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = userEmail,
                onValueChange = { userEmail = it },
                shape = MaterialTheme.shapes.large,
                singleLine = true,
                placeholder = {
                    BasicText(
                        WrStrings.userEmail(),
                        style = MaterialTheme
                            .typography
                            .titleSmall
                            .copy(color = WriteopiaTheme.colorScheme.textLighter)
                    )
                },
                textStyle = MaterialTheme
                    .typography
                    .titleSmall
                    .copy(color = MaterialTheme.colorScheme.onBackground)
            )

            Spacer(modifier = Modifier.width(8.dp))

            CommonButton(text = WrStrings.add()) {
                addUserToTeam(userEmail)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (val usersResult = usersInSelectedWorkspace.collectAsState(ResultData.Idle()).value) {
            is ResultData.Complete -> {
                val users = usersResult.data

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (users.isNotEmpty()) {
                        users.forEach { userName ->
                            BasicText(
                                text = userName,
                                style = MaterialTheme
                                    .typography
                                    .bodySmall
                                    .copy(MaterialTheme.colorScheme.onBackground)
                            )
                        }
                    } else {
                        BasicText(
                            text = "No users in this team",
                            style = MaterialTheme
                                .typography
                                .bodySmall
                                .copy(MaterialTheme.colorScheme.onBackground)
                        )
                    }
                }
            }

            is ResultData.Error -> {
                BasicText(
                    text = "Error loading users",
                    style = MaterialTheme
                        .typography
                        .bodySmall
                        .copy(MaterialTheme.colorScheme.onBackground)
                )
            }

            else -> {
                CircularProgressIndicator()
            }
        }
    } else {
        BasicText(
            text = "Only admins can add users",
            style = MaterialTheme
                .typography
                .bodySmall
                .copy(MaterialTheme.colorScheme.onBackground)
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

            // Only show Dynamic color option on mobile (not on web)
            val currentPlatform = LocalPlatform.current
            if (currentPlatform.isMobile()) {
                Spacer(modifier = Modifier.width(spaceWidth))

                DynamicColorOption(
                    isSelected = selectedColor == AccentColor.DYNAMIC,
                    onClick = { selectAccentColor(AccentColor.DYNAMIC) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.DynamicColorOption(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val typography = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
    val textColor = WriteopiaTheme.colorScheme.textLight

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

    val iconTint = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        WriteopiaTheme.colorScheme.tintLight
    }

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
            Icon(
                modifier = Modifier.size(iconSize),
                imageVector = WrIcons.dynamicColor,
                contentDescription = WrStrings.dynamicColor(),
                tint = iconTint
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                WrStrings.dynamicColor(),
                style = typography,
                color = textColor
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

@Composable
private fun SigningOutDialog() {
    Dialog(
        onDismissRequest = { /* Non-dismissible while signing out */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(modifier = Modifier, shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    WrStrings.signingOut(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
