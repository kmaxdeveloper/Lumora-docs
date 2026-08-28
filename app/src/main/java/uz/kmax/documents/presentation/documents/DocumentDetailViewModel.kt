package uz.kmax.documents.presentation.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uz.kmax.documents.domain.model.Document
import uz.kmax.documents.domain.model.ai.DocumentContext
import uz.kmax.documents.domain.repository.DocumentRepository
import uz.kmax.documents.domain.usecase.GeneratePdfUseCase
import uz.kmax.documents.domain.usecase.ai.PrepareDocumentForAiUseCase

class DocumentDetailViewModel(
    private val repository: DocumentRepository,
    private val generatePdfUseCase: GeneratePdfUseCase,
    private val prepareDocumentForAiUseCase: PrepareDocumentForAiUseCase
) : ViewModel() {

    private val _currentDocument = MutableStateFlow<Document?>(null)
    val currentDocument: StateFlow<Document?> = _currentDocument.asStateFlow()

    private val _documentContext = MutableStateFlow<DocumentContext?>(null)
    val documentContext: StateFlow<DocumentContext?> = _documentContext.asStateFlow()

    private val _pdfState = MutableStateFlow<PdfState>(PdfState.Idle)
    val pdfState: StateFlow<PdfState> = _pdfState.asStateFlow()

    private var observeJob: Job? = null

    fun loadDocument(id: String) {
        observeJob?.cancel()
        observeJob = repository.observeDocument(id)
            .onEach { document ->
                _currentDocument.value = document
                /* AI context preparation disabled for V1
                if (document != null && document.hasOcr) {
                    prepareAiContext(document)
                }
                */
            }
            .launchIn(viewModelScope)
    }

    private fun prepareAiContext(document: Document) {
        viewModelScope.launch {
            _documentContext.value = prepareDocumentForAiUseCase(document)
        }
    }

    fun generatePdf() {
        val document = _currentDocument.value ?: return
        _pdfState.value = PdfState.Generating
        viewModelScope.launch {
            val updated = generatePdfUseCase(document)
            if (updated != null) {
                _currentDocument.value = updated
                _pdfState.value = PdfState.Success
            } else {
                _pdfState.value = PdfState.Error("Couldn't create the PDF. Please try again.")
            }
        }
    }

    fun resetPdfState() {
        _pdfState.value = PdfState.Idle
    }

    fun deletePdf() {
        val document = _currentDocument.value ?: return
        viewModelScope.launch {
            val updated = repository.deletePdf(document)
            if (updated != null) {
                _currentDocument.value = updated
            }
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            repository.deleteDocument(document)
        }
    }

    fun renameDocument(document: Document, newName: String) {
        viewModelScope.launch {
            val updated = document.copy(name = newName, updatedAt = System.currentTimeMillis())
            repository.updateDocument(updated)
            _currentDocument.value = updated
        }
    }

    fun duplicateDocument() {
        val document = _currentDocument.value ?: return
        viewModelScope.launch {
            repository.duplicateDocument(document)
        }
    }
}

sealed class PdfState {
    object Idle : PdfState()
    object Generating : PdfState()
    object Success : PdfState()
    data class Error(val message: String) : PdfState()
}
