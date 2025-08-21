package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.editor.model.EditState
import io.writeopia.model.Font
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.sdk.models.span.Span
import io.writeopia.ui.backstack.BackstackHandler
import io.writeopia.ui.backstack.BackstackInform
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.DrawState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NoteEditorViewModel : BackstackInform, BackstackHandler {

    val writeopiaManager: WriteopiaStateManager

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

    val listenForFolders: StateFlow<List<MenuItemUi.FolderUi>>

    val loadingState: StateFlow<Boolean>

    val notFavorite: StateFlow<Boolean>

    val showSearch: StateFlow<Boolean>

    val searchText: StateFlow<String>

    fun showSearch()

    fun hideSearch()

    fun searchInDocument(query: String)

    fun toggleEditable()

    fun deleteSelection()

    fun handleBackAction(navigateBack: () -> Unit)

    fun onHeaderClick()

    fun createNewDocument(documentId: String, title: String)

    fun loadDocument(documentId: String)

    fun onHeaderColorSelection(color: Int?)

    fun onHeaderEditionCancel()

    fun onMoreOptionsClick()

    fun shareDocumentInJson()

    fun shareDocumentInMarkdown()

    fun onViewModelCleared()

    fun onAddSpanClick(span: Span)

    fun onAddCheckListClick()

    fun onAddListItemClick()

    fun onAddCodeBlockClick()

    fun toggleHighLightBlock()

    fun clearSelections()

    fun changeFontFamily(font: Font)

    fun addImage(imagePath: String)

    fun exportMarkdown(path: String)

    fun exportJson(path: String)

    fun expandFolder(folderId: String)

    fun moveToFolder(folderId: String)

    fun moveToRootFolder()

    fun askAiBySelection()

    fun aiSummary()

    fun aiActionPoints()

    fun aiFaq()

    fun aiTags()

    fun aiSection(position: Int)

    fun addPage()

    fun copySelection()

    fun cutSelection()

    fun deleteDocument()

    fun toggleFavorite()

    fun receiveExternalFile(files: List<ExternalFile>, position: Int)

    fun setTheme(isDarkTheme: Boolean)

    fun selectModel(model: String)
}

data class ShareDocument(val content: String, val title: String, val type: String)
