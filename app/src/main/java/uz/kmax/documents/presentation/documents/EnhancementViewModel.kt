package uz.kmax.documents.presentation.documents

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uz.kmax.documents.domain.model.Document
import uz.kmax.documents.domain.model.DocumentEnhancementMode
import uz.kmax.documents.domain.model.PremiumEntitlement
import uz.kmax.documents.domain.repository.DocumentRepository
import uz.kmax.documents.domain.repository.BillingRepository
import uz.kmax.documents.domain.usecase.EnhanceDocumentUseCase
import java.io.File

class EnhancementViewModel(
    private val repository: DocumentRepository,
    private val enhanceUseCase: EnhanceDocumentUseCase,
    private val billingRepository: BillingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<EnhancementUiState>(EnhancementUiState.Idle)
    val uiState: StateFlow<EnhancementUiState> = _uiState.asStateFlow()

    private var originalBitmap: Bitmap? = null
    private var currentProcessJob: Job? = null
    private var currentDocument: Document? = null

    fun loadDocument(id: String) {
        viewModelScope.launch {
            val doc = repository.getDocument(id) ?: return@launch
            currentDocument = doc
            val options = BitmapFactory.Options().apply { inMutable = true }
            val bitmap = BitmapFactory.decodeFile(doc.imagePath, options) ?: return@launch
            originalBitmap = bitmap
            _uiState.value = EnhancementUiState.Preview(bitmap, DocumentEnhancementMode.ORIGINAL)
        }
    }

    fun setMode(mode: DocumentEnhancementMode) {
        val bitmap = originalBitmap ?: return
        
        if (mode == DocumentEnhancementMode.AUTO && billingRepository.entitlement.value != PremiumEntitlement.PREMIUM) {
            _uiState.value = EnhancementUiState.PremiumRequired
            return
        }

        currentProcessJob?.cancel()
        _uiState.value = EnhancementUiState.Processing
        
        currentProcessJob = viewModelScope.launch {
            try {
                val enhanced = if (mode == DocumentEnhancementMode.ORIGINAL) {
                    bitmap
                } else {
                    enhanceUseCase(bitmap, mode)
                }
                
                _uiState.value = EnhancementUiState.Preview(enhanced, mode)
            } catch (e: Exception) {
                _uiState.value = EnhancementUiState.Error("Enhancement failed")
            }
        }
    }

    fun applyEnhancement() {
        val doc = currentDocument ?: return
        val state = _uiState.value
        if (state !is EnhancementUiState.Preview) return
        
        viewModelScope.launch {
            _uiState.value = EnhancementUiState.Processing
            val result = repository.saveEnhancedImage(doc, state.bitmap)
            if (result != null) {
                _uiState.value = EnhancementUiState.Success
            } else {
                _uiState.value = EnhancementUiState.Error("Failed to save enhanced image")
            }
        }
    }
}

sealed class EnhancementUiState {
    object Idle : EnhancementUiState()
    object Processing : EnhancementUiState()
    data class Preview(val bitmap: Bitmap, val mode: DocumentEnhancementMode) : EnhancementUiState()
    object PremiumRequired : EnhancementUiState()
    object Success : EnhancementUiState()
    data class Error(val message: String) : EnhancementUiState()
}
