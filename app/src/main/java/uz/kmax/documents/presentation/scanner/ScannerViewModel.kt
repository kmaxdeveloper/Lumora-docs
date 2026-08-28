package uz.kmax.documents.presentation.scanner

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

import uz.kmax.documents.domain.scanner.DocumentDetectionResult
import uz.kmax.documents.domain.scanner.StabilityResult

class ScannerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Idle)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()
    
    private val _detectionState = MutableStateFlow<DocumentDetectionResult?>(null)
    val detectionState: StateFlow<DocumentDetectionResult?> = _detectionState.asStateFlow()

    private val _stabilityState = MutableStateFlow(StabilityResult.NOT_DETECTED)
    val stabilityState: StateFlow<StabilityResult> = _stabilityState.asStateFlow()

    private var _lastRawDetection: DocumentDetectionResult? = null
    val lastRawDetection: DocumentDetectionResult? get() = _lastRawDetection

    fun onCaptureStarted() {
        _uiState.value = ScannerUiState.Capturing
    }

    fun onCaptureSuccess(file: File) {
        _uiState.value = ScannerUiState.CaptureSuccess(file)
    }

    fun onCaptureError(message: String) {
        _uiState.value = ScannerUiState.Error(message)
    }
    
    fun onDocumentDetected(rawResult: DocumentDetectionResult, transformedResult: DocumentDetectionResult) {
        _lastRawDetection = rawResult
        _detectionState.value = transformedResult
    }

    fun onStabilityEvaluated(result: StabilityResult) {
        _stabilityState.value = result
    }

    fun resetState() {
        _uiState.value = ScannerUiState.Idle
        _detectionState.value = null
        _lastRawDetection = null
        _stabilityState.value = StabilityResult.NOT_DETECTED
    }
}

sealed class ScannerUiState {
    object Idle : ScannerUiState()
    object Capturing : ScannerUiState()
    data class CaptureSuccess(val file: File) : ScannerUiState()
    data class Error(val message: String) : ScannerUiState()
}
