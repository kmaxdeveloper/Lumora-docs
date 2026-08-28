package uz.kmax.documents.presentation.ocr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uz.kmax.documents.data.local.PreferenceManager
import uz.kmax.documents.domain.model.Document
import uz.kmax.documents.domain.model.PremiumEntitlement
import uz.kmax.documents.domain.model.ocr.DocumentPageOcr
import uz.kmax.documents.domain.repository.DocumentRepository
import uz.kmax.documents.domain.repository.OcrRepository
import uz.kmax.documents.domain.repository.BillingRepository
import uz.kmax.documents.domain.usecase.ExtractDocumentTextUseCase
import java.text.SimpleDateFormat
import java.util.*

class OcrViewModel(
    private val documentRepository: DocumentRepository,
    private val ocrRepository: OcrRepository,
    private val extractTextUseCase: ExtractDocumentTextUseCase,
    private val billingRepository: BillingRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<OcrUiState>(OcrUiState.Idle)
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    private var currentDocument: Document? = null
    private var ocrJob: Job? = null

    fun loadDocument(id: String) {
        viewModelScope.launch {
            val doc = documentRepository.getDocument(id)
            currentDocument = doc
            if (doc != null) {
                observeOcrResults(doc.id)
            }
        }
    }

    private fun observeOcrResults(documentId: String) {
        ocrRepository.observeDocumentOcr(documentId)
            .onEach { results ->
                if (results.isNotEmpty() && _uiState.value is OcrUiState.Idle) {
                    _uiState.value = OcrUiState.Success(results)
                }
            }
            .launchIn(viewModelScope)
    }

    fun extractText() {
        val doc = currentDocument ?: return
        
        if (billingRepository.entitlement.value != PremiumEntitlement.PREMIUM) {
            if (isDailyLimitReached()) {
                _uiState.value = OcrUiState.PremiumRequired
                return
            }
        }

        ocrJob?.cancel()
        
        ocrJob = viewModelScope.launch {
            try {
                _uiState.value = OcrUiState.Processing(0, doc.pages.size.coerceAtLeast(1))
                
                val success = extractTextUseCase(doc) { current, total ->
                    _uiState.value = OcrUiState.Processing(current, total)
                }

                if (success) {
                    incrementOcrCount()
                    val finalResults = ocrRepository.getDocumentOcr(doc.id)
                    _uiState.value = OcrUiState.Success(finalResults)
                } else {
                    _uiState.value = OcrUiState.Error("No readable text found")
                }
            } catch (e: Exception) {
                _uiState.value = OcrUiState.Error("Couldn't read text from this document.")
            }
        }
    }

    fun cancelOcr() {
        ocrJob?.cancel()
        _uiState.value = OcrUiState.Idle
        currentDocument?.let { observeOcrResults(it.id) }
    }

    private fun isDailyLimitReached(): Boolean {
        checkAndResetDailyCount()
        return preferenceManager.ocrDailyCount >= 3
    }

    private fun incrementOcrCount() {
        if (billingRepository.entitlement.value != PremiumEntitlement.PREMIUM) {
            checkAndResetDailyCount()
            preferenceManager.ocrDailyCount++
        }
    }

    private fun checkAndResetDailyCount() {
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
        val todayStr = sdf.format(Date(now))
        val lastDateStr = sdf.format(Date(preferenceManager.ocrLastDate))

        if (todayStr != lastDateStr) {
            preferenceManager.ocrDailyCount = 0
            preferenceManager.ocrLastDate = now
        }
    }
}

sealed interface OcrUiState {
    object Idle : OcrUiState
    data class Processing(val current: Int, val total: Int) : OcrUiState
    object PremiumRequired : OcrUiState
    data class Success(val pages: List<DocumentPageOcr>) : OcrUiState
    data class Error(val message: String) : OcrUiState
}
