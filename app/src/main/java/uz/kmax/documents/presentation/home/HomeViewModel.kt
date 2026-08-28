package uz.kmax.documents.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.kmax.documents.domain.model.Document
import uz.kmax.documents.domain.repository.DocumentRepository

class HomeViewModel(
    private val repository: DocumentRepository
) : ViewModel() {

    val recentDocuments: StateFlow<List<Document>> = repository.observeRecentDocuments(5)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun renameDocument(document: Document, newName: String) {
        viewModelScope.launch {
            repository.updateDocument(document.copy(name = newName, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            repository.deleteDocument(document)
        }
    }
}
