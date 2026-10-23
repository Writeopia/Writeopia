package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.editor.features.presentation.viewmodel.PresentationsController
import io.writeopia.editor.model.EditState
import io.writeopia.model.Font
import io.writeopia.sdk.model.story.Selection
import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.comment.CommentConversation
import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.story.Tag
import io.writeopia.ui.backstack.BackstackHandler
import io.writeopia.ui.backstack.BackstackInform
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.DrawState
import io.writeopia.ui.model.SelectionMetadata
import io.writeopia.writingagent.controller.WritingAgentController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NoteEditorViewModel : BackstackInform, BackstackHandler {

    val writeopiaManager: WriteopiaStateManager

    /** The presentations of the document; null when nothing can make them. */
    val presentations: PresentationsController?

    /** The writing agent that suggests what to add while the user writes; null when the app has none. */
    val writingAgent: WritingAgentController?

    val isEditable: StateFlow<Boolean>

    val currentModel: Flow<String>

    val models: Flow<List<String>>

    val showGlobalMenu: StateFlow<Boolean>

    val editHeader: StateFlow<Boolean>

    val currentTitle: StateFlow<String>

    val shouldGoToNextScreen: StateFlow<Boolean>

    val isEditState: StateFlow<EditState>

    val scrollToPosition: StateFlow<Int?>

    val toDrawWithDecoration: StateFlow<DrawState>

    val documentToShareInfo: StateFlow<ShareDocument?>

    val fontFamily: StateFlow<Font>

    val loadingState: StateFlow<Boolean>

    val notFavorite: StateFlow<Boolean>

    val showSearchState: StateFlow<Boolean>

    val searchText: StateFlow<String>

    val currentSearchIndexState: StateFlow<Int>

    val totalSearchResultsState: StateFlow<Int>

    val hasSelectedLines: StateFlow<Boolean>

    val isWorkspaceOffline: StateFlow<Boolean>

    val selectionMetadataState: StateFlow<Set<SelectionMetadata>>

    val commentConversations: StateFlow<Map<String, List<Comment>>>

    val sideMenuTabState: StateFlow<SideMenuTab>

    val showPublishDialog: StateFlow<Boolean>

    val isDocumentPublished: StateFlow<Boolean>

    val publishLoading: StateFlow<Boolean>

    val showPremiumDialog: StateFlow<Boolean>

    /** The tags of the open document, with their names and explanations from the catalogue. */
    val documentTags: StateFlow<List<DocumentTag>>

    /** The tag catalogue of the workspace, by name, without tombstones. */
    val workspaceTags: StateFlow<List<DocumentTag>>

    val showTagsDialog: StateFlow<Boolean>

    val showAddTagDialog: StateFlow<Boolean>

    /** True when the cloud AI can make up a tag: signed in, online workspace, agent available. */
    val canSuggestTagWithAi: StateFlow<Boolean>

    val aiTagDraft: StateFlow<AiTagDraft>

    fun changeSideMenu(tab: SideMenuTab)

    fun showTagsDialog()

    fun hideTagsDialog()

    fun showAddTagDialog()

    fun hideAddTagDialog()

    fun addTagToDocument(tagId: String)

    fun removeTagFromDocument(tagId: String)

    /** Makes a tag in the catalogue and adds it to the document. */
    fun createTag(name: String, description: String)

    /** Asks the cloud AI for a tag; the answer lands in [aiTagDraft] for the user to confirm. */
    fun suggestTagWithAi()

    fun clearAiTagDraft()

    fun showSearch()

    fun hideSearch()

    fun searchInDocument(query: String)

    fun previousSearchResult()

    fun nextSearchResult()

    fun toggleEditable()

    fun deleteSelection()

    fun handleBackAction(navigateBack: () -> Unit)

    fun onHeaderClick()

    fun createNewDocument(documentId: String, title: String)

    fun loadDocument(documentId: String)

    fun onHeaderColorSelection(color: Int?)

    /** The header pictures of the workspace and the one the AI is drawing, for the header menu. */
    val headerImages: StateFlow<HeaderImagesState>

    /** Puts a picture of the workspace in the header; null removes it. */
    fun onHeaderImageSelection(url: String?)

    /** Asks the AI for a header picture, or for a different one. It costs tokens of the user. */
    fun generateHeaderImage()

    /** Puts the drawn picture in the header and keeps it with the pictures of the workspace. */
    fun approveHeaderImage()

    /** Drops the drawn picture, or stops drawing it. */
    fun discardHeaderImage()

    fun onHeaderEditionCancel()

    fun onMoreOptionsClick()

    fun shareDocumentInJson()

    fun shareDocumentInMarkdown()

    fun onViewModelCleared()

    fun onAddSpanClick(span: Span)

    fun createComment(text: String): CommentConversation?

    fun createComment(text: String, target: Selection): CommentConversation?

    fun addComment(conversationId: String, text: String): Comment?

    fun getCommentConversationAtCursor(): CommentConversation?

    fun getCommentConversationAtSelection(): CommentConversation?

    fun deleteComment(conversationId: String, commentId: String): Boolean

    fun deleteCommentConversation(conversationId: String): Boolean

    fun onAddCheckListClick()

    fun onAddListItemClick()

    fun onAddCodeBlockClick()

    fun toggleHighLightBlock()

    fun toggleCardBlock()

    fun clearSelections()

    fun changeFontFamily(font: Font)

    /** Adds the image at [position], or at the cursor without one. */
    fun addImage(imagePath: String, position: String? = null)

    fun exportMarkdown(path: String)

    fun exportJson(path: String)

    /** Every folder of the workspace, for the "move to" picker. */
    suspend fun workspaceFolders(): List<Folder>

    /** The folder the document is in. */
    fun documentFolderId(): String

    fun moveToFolder(folderId: String)

    fun askAiWithMode(targetMode: AiTargetMode)

    fun aiSummary(targetMode: AiTargetMode)

    fun aiActionPoints(targetMode: AiTargetMode)

    fun aiFaq(targetMode: AiTargetMode)

    fun aiTags(targetMode: AiTargetMode)

    fun aiSection(position: String)

    /**
     * Runs a free [command] typed by the user about the document and the lines selected right
     * now, if any. The AI decides whether its answer replaces those lines or is added to the
     * document.
     */
    fun aiCommand(command: String)

    fun addPage()

    fun copySelection()

    fun cutSelection()

    fun deleteDocument()

    fun toggleFavorite()

    fun receiveExternalFile(files: List<ExternalFile>, position: String)

    fun setTheme(isDarkTheme: Boolean)

    fun selectModel(model: String)

    fun titleClick(tag: Tag)

    fun showPublishDialog()

    fun hidePublishDialog()

    fun hidePremiumDialog()

    fun publishDocument()

    fun unpublishDocument()

    fun copyPublishLink()

    fun onAddSpreadsheetClick(columnCount: Int)
}

data class ShareDocument(val content: String, val title: String, val type: String)

public enum class SideMenuTab {
    NONE,
    PAGE_STYLE,
    TEXT_OPTIONS,
    EXPORT,
    AI,
    DRAWING
}

public enum class AiTargetMode {
    DOCUMENT,
    SELECTED_LINES,
    CURSOR
}
