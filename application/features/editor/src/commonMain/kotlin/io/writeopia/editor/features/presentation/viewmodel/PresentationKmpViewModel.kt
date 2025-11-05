package io.writeopia.editor.features.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.presentation.model.SlidePage
import io.writeopia.sdk.presentation.parse.PresentationParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PresentationKmpViewModel(
    private val documentRepository: DocumentRepository,
    private val presentationParser: PresentationParser = PresentationParser
) : ViewModel(), PresentationViewModel {

    private val _currentPage = MutableStateFlow(0)

    private val _slidesState = MutableStateFlow<List<SlidePage>>(emptyList())

    override val currentPage: StateFlow<Int> = _currentPage.asStateFlow()
    override val slidesState: StateFlow<List<SlidePage>> = _slidesState.asStateFlow()

    override fun previousPage() {
        moveSlide(_currentPage.value - 1)
    }

    override fun nextPage() {
        moveSlide(_currentPage.value + 1)
    }

    private fun moveSlide(position: Int) {
        val lastIndex = _slidesState.value.lastIndex
        val limitTop = if (position >= lastIndex) lastIndex else position
        val limit = if (limitTop < 0) 0 else limitTop
        _currentPage.value = limit
    }

    override fun loadDocument(id: String) {
//        viewModelScope.launch {
//            documentRepository.loadDocumentById(id)?.let { document ->
//                _slidesState.value = presentationParser.parse(document.content.values)
//            }
//        }
    }
}
