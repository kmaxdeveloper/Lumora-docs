package uz.kmax.documents.presentation.scanner

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uz.kmax.documents.domain.repository.DocumentRepository

class ImagePreviewViewModel(
    private val repository: DocumentRepository
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun saveDocument(bitmap: Bitmap) {
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            val result = repository.saveDocument(bitmap)
            if (result != null) {
                _saveState.value = SaveState.Success
            } else {
                _saveState.value = SaveState.Error("Failed to save document")
            }
        }
    }
}

sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    object Success : SaveState()
    data class Error(val message: String) : SaveState()
}
