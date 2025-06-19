package io.writeopia.resources

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import writeopia.application.core.resources.generated.resources.Res
import writeopia.application.core.resources.generated.resources.access_ollama_site
import writeopia.application.core.resources.generated.resources.account
import writeopia.application.core.resources.generated.resources.action_points
import writeopia.application.core.resources.generated.resources.actions
import writeopia.application.core.resources.generated.resources.are_you_sure
import writeopia.application.core.resources.generated.resources.arrangement
import writeopia.application.core.resources.generated.resources.ask_ai
import writeopia.application.core.resources.generated.resources.available_models
import writeopia.application.core.resources.generated.resources.box
import writeopia.application.core.resources.generated.resources.cancel
import writeopia.application.core.resources.generated.resources.change_account
import writeopia.application.core.resources.generated.resources.choose_your_model
import writeopia.application.core.resources.generated.resources.close
import writeopia.application.core.resources.generated.resources.color_theme
import writeopia.application.core.resources.generated.resources.company
import writeopia.application.core.resources.generated.resources.confirm
import writeopia.application.core.resources.generated.resources.confirmation_delete_multiple_items
import writeopia.application.core.resources.generated.resources.content
import writeopia.application.core.resources.generated.resources.copy_note
import writeopia.application.core.resources.generated.resources.create_account
import writeopia.application.core.resources.generated.resources.create_your_account
import writeopia.application.core.resources.generated.resources.dark_theme
import writeopia.application.core.resources.generated.resources.decoration
import writeopia.application.core.resources.generated.resources.delete
import writeopia.application.core.resources.generated.resources.delete_account
import writeopia.application.core.resources.generated.resources.dismiss
import writeopia.application.core.resources.generated.resources.document
import writeopia.application.core.resources.generated.resources.download_model
import writeopia.application.core.resources.generated.resources.download_models
import writeopia.application.core.resources.generated.resources.download_ollama
import writeopia.application.core.resources.generated.resources.email
import writeopia.application.core.resources.generated.resources.error_model_download
import writeopia.application.core.resources.generated.resources.error_requesting_models
import writeopia.application.core.resources.generated.resources.export
import writeopia.application.core.resources.generated.resources.export_json
import writeopia.application.core.resources.generated.resources.export_markdown
import writeopia.application.core.resources.generated.resources.export_txt
import writeopia.application.core.resources.generated.resources.favorite
import writeopia.application.core.resources.generated.resources.favorites
import writeopia.application.core.resources.generated.resources.folder
import writeopia.application.core.resources.generated.resources.font
import writeopia.application.core.resources.generated.resources.general
import writeopia.application.core.resources.generated.resources.highlight
import writeopia.application.core.resources.generated.resources.home
import writeopia.application.core.resources.generated.resources.image
import writeopia.application.core.resources.generated.resources.import_file
import writeopia.application.core.resources.generated.resources.insert
import writeopia.application.core.resources.generated.resources.jorney_starts
import writeopia.application.core.resources.generated.resources.json
import writeopia.application.core.resources.generated.resources.last_created
import writeopia.application.core.resources.generated.resources.last_updated
import writeopia.application.core.resources.generated.resources.light_theme
import writeopia.application.core.resources.generated.resources.links
import writeopia.application.core.resources.generated.resources.local_folder
import writeopia.application.core.resources.generated.resources.lock_document
import writeopia.application.core.resources.generated.resources.logout
import writeopia.application.core.resources.generated.resources.markdown
import writeopia.application.core.resources.generated.resources.move_to
import writeopia.application.core.resources.generated.resources.move_to_home
import writeopia.application.core.resources.generated.resources.name
import writeopia.application.core.resources.generated.resources.new_password
import writeopia.application.core.resources.generated.resources.no_models
import writeopia.application.core.resources.generated.resources.notes_will_be_deleted
import writeopia.application.core.resources.generated.resources.ok
import writeopia.application.core.resources.generated.resources.ollama
import writeopia.application.core.resources.generated.resources.ollama_configuration_complete
import writeopia.application.core.resources.generated.resources.onboarding_explain1
import writeopia.application.core.resources.generated.resources.onboarding_hello
import writeopia.application.core.resources.generated.resources.onboarding_select_ai
import writeopia.application.core.resources.generated.resources.or_word
import writeopia.application.core.resources.generated.resources.page
import writeopia.application.core.resources.generated.resources.password
import writeopia.application.core.resources.generated.resources.reset_password
import writeopia.application.core.resources.generated.resources.private_ai_enabled
import writeopia.application.core.resources.generated.resources.recent
import writeopia.application.core.resources.generated.resources.repeat_password
import writeopia.application.core.resources.generated.resources.retry
import writeopia.application.core.resources.generated.resources.search
import writeopia.application.core.resources.generated.resources.settings
import writeopia.application.core.resources.generated.resources.sign_in
import writeopia.application.core.resources.generated.resources.sign_in_account
import writeopia.application.core.resources.generated.resources.small_robot
import writeopia.application.core.resources.generated.resources.sort_by_creation
import writeopia.application.core.resources.generated.resources.sort_by_name
import writeopia.application.core.resources.generated.resources.sort_by_update
import writeopia.application.core.resources.generated.resources.sorting
import writeopia.application.core.resources.generated.resources.start_now
import writeopia.application.core.resources.generated.resources.suggestions
import writeopia.application.core.resources.generated.resources.summarize
import writeopia.application.core.resources.generated.resources.system_theme
import writeopia.application.core.resources.generated.resources.tap_to_start
import writeopia.application.core.resources.generated.resources.text
import writeopia.application.core.resources.generated.resources.type_new_password
import writeopia.application.core.resources.generated.resources.url
import writeopia.application.core.resources.generated.resources.version
import writeopia.application.core.resources.generated.resources.workspaceName
import writeopia.application.core.resources.generated.resources.you_are_offline
import writeopia.application.core.resources.generated.resources.use_offline

object WrStrings {

    @Composable
    fun search() = stringResource(Res.string.search)

    @Composable
    fun home() = stringResource(Res.string.home)

    @Composable
    fun favorites() = stringResource(Res.string.favorites)

    @Composable
    fun settings() = stringResource(Res.string.settings)

    @Composable
    fun folder() = stringResource(Res.string.folder)

    @Composable
    fun recent() = stringResource(Res.string.recent)

    @Composable
    fun colorTheme() = stringResource(Res.string.color_theme)

    @Composable
    fun localFolder() = stringResource(Res.string.local_folder)

    @Composable
    fun ollama() = stringResource(Res.string.ollama)

    @Composable
    fun url() = stringResource(Res.string.url)

    @Composable
    fun availableModels() = stringResource(Res.string.available_models)

    @Composable
    fun noModelsFound() = stringResource(Res.string.no_models)

    @Composable
    fun errorRequestingModels() = stringResource(Res.string.error_requesting_models)

    @Composable
    fun retry() = stringResource(Res.string.retry)

    @Composable
    fun downloadModels() = stringResource(Res.string.download_models)

    @Composable
    fun suggestions() = stringResource(Res.string.suggestions)

    @Composable
    fun errorModelDownload() = stringResource(Res.string.error_model_download)

    @Composable
    fun version() = stringResource(Res.string.version)

    @Composable
    fun lightTheme() = stringResource(Res.string.light_theme)

    @Composable
    fun darkTheme() = stringResource(Res.string.dark_theme)

    @Composable
    fun systemTheme() = stringResource(Res.string.system_theme)

    @Composable
    fun exportMarkdown() = stringResource(Res.string.export_markdown)

    @Composable
    fun exportAsTxt() = stringResource(Res.string.export_txt)

    @Composable
    fun exportJson() = stringResource(Res.string.export_json)

    @Composable
    fun importFile() = stringResource(Res.string.import_file)

    @Composable
    fun sortByName() = stringResource(Res.string.sort_by_name)

    @Composable
    fun sortByCreation() = stringResource(Res.string.sort_by_creation)

    @Composable
    fun sortByUpdate() = stringResource(Res.string.sort_by_update)

    @Composable
    fun lockDocument() = stringResource(Res.string.lock_document)

    @Composable
    fun moveTo() = stringResource(Res.string.move_to)

    @Composable
    fun moveToHome() = stringResource(Res.string.move_to_home)

    @Composable
    fun text() = stringResource(Res.string.text)

    @Composable
    fun highlighting() = stringResource(Res.string.highlight)

    @Composable
    fun insert() = stringResource(Res.string.insert)

    @Composable
    fun decoration() = stringResource(Res.string.decoration)

    @Composable
    fun box() = stringResource(Res.string.box)

    @Composable
    fun content() = stringResource(Res.string.content)

    @Composable
    fun image() = stringResource(Res.string.image)

    @Composable
    fun links() = stringResource(Res.string.links)

    @Composable
    fun page() = stringResource(Res.string.page)

    @Composable
    fun askAi() = stringResource(Res.string.ask_ai)

    @Composable
    fun summary() = stringResource(Res.string.summarize)

    @Composable
    fun actionPoints() = stringResource(Res.string.action_points)

    @Composable
    fun export() = stringResource(Res.string.export)

    @Composable
    fun json() = stringResource(Res.string.json)

    @Composable
    fun markdown() = stringResource(Res.string.markdown)

    @Composable
    fun tapToStart() = stringResource(Res.string.tap_to_start)

    @Composable
    fun arrangement() = stringResource(Res.string.arrangement)

    @Composable
    fun sorting() = stringResource(Res.string.sorting)

    @Composable
    fun lastUpdated() = stringResource(Res.string.last_updated)

    @Composable
    fun created() = stringResource(Res.string.last_created)

    @Composable
    fun name() = stringResource(Res.string.name)

    @Composable
    fun font() = stringResource(Res.string.font)

    @Composable
    fun actions() = stringResource(Res.string.actions)

    @Composable
    fun onboardingHello() = stringResource(Res.string.onboarding_hello)

    @Composable
    fun onboardingTutorialExplain() = stringResource(Res.string.onboarding_explain1)

    @Composable
    fun onboardingChooseAi() = stringResource(Res.string.onboarding_select_ai)

    @Composable
    fun close() = stringResource(Res.string.close)

    @Composable
    fun downloadOllama() = stringResource(Res.string.download_ollama)

    @Composable
    fun accessOllamaSite() = stringResource(Res.string.access_ollama_site)

    @Composable
    fun ollamaConfigComplete() = stringResource(Res.string.ollama_configuration_complete)

    @Composable
    fun privateAiEnabled() = stringResource(Res.string.private_ai_enabled)

    @Composable
    fun smallRobot() = stringResource(Res.string.small_robot)

    @Composable
    fun copyDocument() = stringResource(Res.string.copy_note)

    @Composable
    fun delete() = stringResource(Res.string.delete)

    @Composable
    fun document() = stringResource(Res.string.document)

    @Composable
    fun favorite() = stringResource(Res.string.favorite)

    @Composable
    fun confirmDeleteMultipleItems() = stringResource(Res.string.confirmation_delete_multiple_items)

    @Composable
    fun ok() = stringResource(Res.string.ok)

    @Composable
    fun cancel() = stringResource(Res.string.cancel)

    @Composable
    fun general() = stringResource(Res.string.general)

    @Composable
    fun appearance() = stringResource(Res.string.general)

    @Composable
    fun account() = stringResource(Res.string.account)

    @Composable
    fun workspaceName() = stringResource(Res.string.workspaceName)

    @Composable
    fun singIn() = stringResource(Res.string.sign_in)

    @Composable
    fun youAreOffline() = stringResource(Res.string.you_are_offline)

    @Composable
    fun journeyStarts() = stringResource(Res.string.jorney_starts)

    @Composable
    fun createYourAccount() = stringResource(Res.string.create_your_account)

    @Composable
    fun createAccount() = stringResource(Res.string.create_account)

    @Composable
    fun startNow() = stringResource(Res.string.start_now)

    @Composable
    fun signInToAccount() = stringResource(Res.string.sign_in_account)

    @Composable
    fun or() = stringResource(Res.string.or_word)

    @Composable
    fun password() = stringResource(Res.string.password)

    @Composable
    fun resetPassword() = stringResource(Res.string.reset_password)

    @Composable
    fun typeNewPassword() = stringResource(Res.string.type_new_password)

    @Composable
    fun newPassword() = stringResource(Res.string.new_password)

    @Composable
    fun repeatPassword() = stringResource(Res.string.repeat_password)

    @Composable
    fun email() = stringResource(Res.string.email)

    @Composable
    fun company() = stringResource(Res.string.company)

    @Composable
    fun changeAccount() = stringResource(Res.string.change_account)

    @Composable
    fun logout() = stringResource(Res.string.logout)

    @Composable
    fun deleteAccount() = stringResource(Res.string.delete_account)

    @Composable
    fun areYouSure() = stringResource(Res.string.are_you_sure)

    @Composable
    fun notesWillBeDeleted() = stringResource(Res.string.notes_will_be_deleted)

    @Composable
    fun confirmEmail() = stringResource(Res.string.notes_will_be_deleted)

    @Composable
    fun dismiss() = stringResource(Res.string.dismiss)

    @Composable
    fun confirm() = stringResource(Res.string.confirm)

    @Composable
    fun downloadModel() = stringResource(Res.string.download_model)

    @Composable
    fun writeYourAiModel() = stringResource(Res.string.choose_your_model)

    @Composable
    fun useOffline() = stringResource(Res.string.use_offline)
}
