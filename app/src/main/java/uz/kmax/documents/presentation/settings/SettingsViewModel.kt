package uz.kmax.documents.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.kmax.documents.data.local.PreferenceManager
import uz.kmax.documents.domain.model.PremiumEntitlement
import uz.kmax.documents.domain.repository.DocumentRepository
import uz.kmax.documents.domain.repository.BillingRepository
import java.io.File

class SettingsViewModel(
    private val repository: DocumentRepository,
    private val preferenceManager: PreferenceManager,
    private val billingRepository: BillingRepository
) : ViewModel() {

    val entitlement = billingRepository.entitlement

    fun setDebugNoAds(enabled: Boolean) {
        billingRepository.setDebugEntitlementOverride(
            if (enabled) PremiumEntitlement.PREMIUM else null
        )
    }

    private val _storageInfo = MutableStateFlow<StorageInfo>(StorageInfo())
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()

    fun updateAppearance(mode: Int) {
        preferenceManager.appearance = mode
    }

    fun getAppearance() = preferenceManager.appearance

    fun loadStorageInfo(context: Context) {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                calculateStorage(context)
            }
            _storageInfo.value = info
        }
    }

    fun clearTemporaryFiles(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                context.cacheDir.deleteRecursively()
                context.cacheDir.mkdirs()
            }
            loadStorageInfo(context)
        }
    }

    private suspend fun calculateStorage(context: Context): StorageInfo {
        val docs = repository.observeDocuments().first()
        val docCount = docs.size
        
        val documentsDir = File(context.filesDir, "documents")
        val pdfDir = File(context.filesDir, "pdf")
        
        val docsDiskSize = getDirSize(documentsDir)
        val pdfSize = getDirSize(pdfDir)
        val cacheSize = getDirSize(context.cacheDir)

        return StorageInfo(
            documentCount = docCount,
            totalDocumentsSize = docsDiskSize,
            pdfSize = pdfSize,
            temporarySize = cacheSize
        )
    }

    private fun getDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        var size = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) size += file.length()
        }
        return size
    }
}

data class StorageInfo(
    val documentCount: Int = 0,
    val totalDocumentsSize: Long = 0,
    val pdfSize: Long = 0,
    val temporarySize: Long = 0
)
