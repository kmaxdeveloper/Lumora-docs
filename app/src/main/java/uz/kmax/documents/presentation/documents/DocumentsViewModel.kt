package uz.kmax.documents.presentation.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uz.kmax.documents.domain.model.Document
import uz.kmax.documents.domain.model.PremiumEntitlement
import uz.kmax.documents.domain.repository.DocumentRepository
import uz.kmax.documents.domain.repository.BillingRepository

class DocumentsViewModel(
    private val repository: DocumentRepository,
    private val billingRepository: BillingRepository
) : ViewModel() {

    private val _events = MutableSharedFlow<DocumentsEvent>()
    val events = _events.asSharedFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(DocumentSort.NEWEST)
    val sortOrder: StateFlow<DocumentSort> = _sortOrder.asStateFlow()

    private val _filter = MutableStateFlow(DocumentFilter.ALL)
    val filter: StateFlow<DocumentFilter> = _filter.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedIds.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val documents: StateFlow<List<Document>> = combine(
        repository.observeDocuments(),
        _searchQuery,
        _sortOrder,
        _filter
    ) { allDocs, query, sort, filter ->
        allDocs.asSequence()
            .filter { doc ->
                if (query.isBlank()) true
                else {
                    doc.name.contains(query, ignoreCase = true) || 
                    (doc.ocrText?.contains(query, ignoreCase = true) == true)
                }
            }
            .filter { doc ->
                when (filter) {
                    DocumentFilter.ALL -> true
                    DocumentFilter.PDF -> doc.pdfPath != null
                    DocumentFilter.NO_PDF -> doc.pdfPath == null
                    DocumentFilter.OCR -> doc.hasOcr
                    DocumentFilter.NO_OCR -> !doc.hasOcr
                }
            }
            .sortedWith(getComparator(sort))
            .toList()
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalCount: StateFlow<Int> = repository.observeDocuments()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(sort: DocumentSort) {
        _sortOrder.value = sort
    }

    fun setFilter(filter: DocumentFilter) {
        _filter.value = filter
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _filter.value = DocumentFilter.ALL
    }

    fun toggleSelection(documentId: String) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(documentId)) {
            current.remove(documentId)
        } else {
            current.add(documentId)
        }
        _selectedIds.value = current
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelectedDocuments() {
        if (billingRepository.entitlement.value != PremiumEntitlement.PREMIUM && _selectedIds.value.size > 1) {
            viewModelScope.launch { _events.emit(DocumentsEvent.PremiumRequired) }
            return
        }

        val idsToDelete = _selectedIds.value.toList()
        viewModelScope.launch {
            idsToDelete.forEach { id ->
                repository.getDocument(id)?.let { doc ->
                    repository.deleteDocument(doc)
                }
            }
            clearSelection()
        }
    }

    fun duplicateDocument(document: Document) {
        viewModelScope.launch {
            repository.duplicateDocument(document)
        }
    }

    private fun getComparator(sort: DocumentSort): Comparator<Document> {
        return when (sort) {
            DocumentSort.NEWEST -> compareByDescending { it.updatedAt }
            DocumentSort.OLDEST -> compareBy { it.updatedAt }
            DocumentSort.NAME_AZ -> compareBy { it.name.lowercase() }
            DocumentSort.NAME_ZA -> compareByDescending { it.name.lowercase() }
            DocumentSort.LARGEST -> compareByDescending { it.fileSize }
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            repository.deleteDocument(document)
        }
    }

    fun renameDocument(document: Document, newName: String) {
        viewModelScope.launch {
            repository.updateDocument(document.copy(name = newName, updatedAt = System.currentTimeMillis()))
        }
    }

    fun onBulkShareRequested() {
        if (billingRepository.entitlement.value != PremiumEntitlement.PREMIUM && _selectedIds.value.size > 1) {
            viewModelScope.launch { _events.emit(DocumentsEvent.PremiumRequired) }
        } else {
            viewModelScope.launch { _events.emit(DocumentsEvent.NavigateToBulkShare) }
        }
    }
}

sealed class DocumentsEvent {
    object PremiumRequired : DocumentsEvent()
    object NavigateToBulkShare : DocumentsEvent()
}

enum class DocumentSort { NEWEST, OLDEST, NAME_AZ, NAME_ZA, LARGEST }
enum class DocumentFilter { ALL, PDF, NO_PDF, OCR, NO_OCR }
