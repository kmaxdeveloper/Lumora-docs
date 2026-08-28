package uz.kmax.documents.presentation.scanner

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.kmax.documents.domain.model.Document
import uz.kmax.documents.domain.repository.DocumentRepository
import uz.kmax.documents.utils.BitmapUtils
import java.io.File
import java.io.FileOutputStream
import java.util.*

class ScanSessionViewModel(
    private val repository: DocumentRepository
) : ViewModel() {

    private val _pages = MutableStateFlow<List<String>>(emptyList())
    val pages: StateFlow<List<String>> = _pages.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private var sessionCacheDir: File? = null

    private fun getCacheDir(context: Context): File {
        return sessionCacheDir ?: File(context.cacheDir, "scan_session").apply {
            if (!exists()) mkdirs()
            sessionCacheDir = this
        }
    }

    suspend fun addPageAsync(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        val file = File(getCacheDir(context), "temp_${UUID.randomUUID()}.jpg")
        val success = try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            true
        } catch (e: Exception) {
            false
        }
        if (success) {
            _pages.value += file.absolutePath
            true
        } else {
            false
        }
    }

    fun addPage(context: Context, bitmap: Bitmap, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            addPageAsync(context, bitmap)
            onComplete?.invoke()
        }
    }

    fun importImages(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        
        _importState.value = ImportState.Loading(0, uris.size)
        viewModelScope.launch {
            val paths = mutableListOf<String>()
            uris.forEachIndexed { index, uri ->
                _importState.value = ImportState.Loading(index + 1, uris.size)
                val bitmap = BitmapUtils.decodeUri(context, uri)
                if (bitmap != null) {
                    val path = withContext(Dispatchers.IO) {
                        val file = File(getCacheDir(context), "temp_${UUID.randomUUID()}.jpg")
                        try {
                            FileOutputStream(file).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }
                            file.absolutePath
                        } catch (e: Exception) {
                            null
                        }
                    }
                    path?.let { paths.add(it) }
                    bitmap.recycle()
                }
            }
            _pages.value += paths
            _importState.value = ImportState.Success
        }
    }

    fun resetImportState() {
        _importState.value = ImportState.Idle
    }

    fun removePage(index: Int) {
        val current = _pages.value.toMutableList()
        if (index in current.indices) {
            val path = current.removeAt(index)
            File(path).delete()
            _pages.value = current
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val current = _pages.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val path = current.removeAt(fromIndex)
            current.add(toIndex, path)
            _pages.value = current
        }
    }

    fun saveDocument(name: String? = null) {
        val paths = _pages.value
        if (paths.isEmpty()) return

        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            val result = repository.saveMultiPageDocument(paths, name)
            if (result != null) {
                _saveState.value = SaveState.Success(result)
                clearSessionFiles()
                _pages.value = emptyList()
            } else {
                _saveState.value = SaveState.Error("Failed to save document")
            }
        }
    }

    private fun clearSessionFiles() {
        sessionCacheDir?.deleteRecursively()
        sessionCacheDir = null
    }

    fun clear() {
        clearSessionFiles()
        _pages.value = emptyList()
        _saveState.value = SaveState.Idle
    }

    override fun onCleared() {
        clearSessionFiles()
    }

    sealed interface SaveState {
        object Idle : SaveState
        object Saving : SaveState
        data class Success(val document: Document) : SaveState
        data class Error(val message: String) : SaveState
    }

    sealed interface ImportState {
        object Idle : ImportState
        data class Loading(val current: Int, val total: Int) : ImportState
        object Success : ImportState
        data class Error(val message: String) : ImportState
    }
}
