package uz.kmax.documents.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uz.kmax.documents.domain.model.ai.AiSummary
import uz.kmax.documents.domain.model.ai.DocumentContext
import uz.kmax.documents.domain.usecase.ai.GenerateDocumentSummaryUseCase
import uz.kmax.documents.domain.usecase.ai.PrepareDocumentForAiUseCase
import uz.kmax.documents.domain.repository.DocumentRepository

class SummaryViewModel(
    private val documentRepository: DocumentRepository,
    private val prepareDocumentUseCase: PrepareDocumentForAiUseCase,
    private val generateSummaryUseCase: GenerateDocumentSummaryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<SummaryUiState>(SummaryUiState.Idle)
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    private var currentContext: DocumentContext? = null

    fun loadSummary(documentId: String) {
        _uiState.value = SummaryUiState.Loading
        viewModelScope.launch {
            val doc = documentRepository.getDocument(documentId)
            if (doc == null || doc.ocrText == null) {
                _uiState.value = SummaryUiState.Error("No text found in this document. Please run OCR first.")
                return@launch
            }

            val context = prepareDocumentUseCase(doc)
            if (context == null || context.normalizedText.isBlank()) {
                _uiState.value = SummaryUiState.Error("No readable text found.")
                return@launch
            }
            
            currentContext = context
            generateSummary(context)
        }
    }

    fun retry() {
        viewModelScope.launch {
            currentContext?.let { generateSummary(it) }
        }
    }

    private suspend fun generateSummary(context: DocumentContext) {
        _uiState.value = SummaryUiState.Generating
        val result = generateSummaryUseCase(context)
        
        result.onSuccess { summary ->
            _uiState.value = SummaryUiState.Success(summary)
        }.onFailure { error ->
            _uiState.value = SummaryUiState.Error(error.message ?: "Failed to generate summary")
        }
    }
}

sealed interface SummaryUiState {
    object Idle : SummaryUiState
    object Loading : SummaryUiState
    object Generating : SummaryUiState
    data class Success(val summary: AiSummary) : SummaryUiState
    data class Error(val message: String) : SummaryUiState
}
