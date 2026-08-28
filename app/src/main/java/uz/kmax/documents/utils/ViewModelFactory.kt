package uz.kmax.documents.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import uz.kmax.documents.data.ai.DocumentContextBuilder
import uz.kmax.documents.data.ai.DocumentTextNormalizer
import uz.kmax.documents.data.local.PreferenceManager
import uz.kmax.documents.data.processing.DocumentEnhancementProcessor
import uz.kmax.documents.domain.repository.AiRepository
import uz.kmax.documents.domain.repository.DocumentRepository
import uz.kmax.documents.domain.repository.OcrRepository
import uz.kmax.documents.domain.repository.BillingRepository
import uz.kmax.documents.domain.usecase.EnhanceDocumentUseCase
import uz.kmax.documents.domain.usecase.ExtractDocumentTextUseCase
import uz.kmax.documents.domain.usecase.GeneratePdfUseCase
import uz.kmax.documents.domain.usecase.ai.GenerateDocumentSummaryUseCase
import uz.kmax.documents.domain.usecase.ai.PrepareDocumentForAiUseCase
import uz.kmax.documents.presentation.ai.SummaryViewModel
import uz.kmax.documents.presentation.documents.DocumentDetailViewModel
import uz.kmax.documents.presentation.documents.DocumentsViewModel
import uz.kmax.documents.presentation.documents.EnhancementViewModel
import uz.kmax.documents.presentation.home.HomeViewModel
import uz.kmax.documents.presentation.ocr.OcrViewModel
import uz.kmax.documents.presentation.scanner.ImagePreviewViewModel
import uz.kmax.documents.presentation.scanner.ScanSessionViewModel
import uz.kmax.documents.presentation.settings.SettingsViewModel

class ViewModelFactory(
    private val repository: DocumentRepository,
    private val ocrRepository: OcrRepository? = null,
    private val aiRepository: AiRepository? = null,
    private val preferenceManager: PreferenceManager? = null,
    private val billingRepository: BillingRepository? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val normalizer = DocumentTextNormalizer()
        val builder = DocumentContextBuilder(normalizer)
        val prepareUseCase = PrepareDocumentForAiUseCase(builder)

        return when {
            modelClass.isAssignableFrom(ImagePreviewViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                ImagePreviewViewModel(repository) as T
            }
            modelClass.isAssignableFrom(ScanSessionViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                ScanSessionViewModel(repository, billingRepository!!) as T
            }
            modelClass.isAssignableFrom(DocumentsViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                DocumentsViewModel(repository, billingRepository!!) as T
            }
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                HomeViewModel(repository) as T
            }
            modelClass.isAssignableFrom(DocumentDetailViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                DocumentDetailViewModel(repository, GeneratePdfUseCase(repository), prepareUseCase) as T
            }
            modelClass.isAssignableFrom(EnhancementViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                EnhancementViewModel(repository, EnhanceDocumentUseCase(DocumentEnhancementProcessor()), billingRepository!!) as T
            }
            modelClass.isAssignableFrom(OcrViewModel::class.java) -> {
                val ocrRepo = ocrRepository!!
                @Suppress("UNCHECKED_CAST")
                OcrViewModel(
                    repository, 
                    ocrRepo, 
                    ExtractDocumentTextUseCase(ocrRepo), 
                    billingRepository!!,
                    preferenceManager!!
                ) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                SettingsViewModel(repository, preferenceManager!!, billingRepository!!) as T
            }
            /* AI Summary disabled for V1
            modelClass.isAssignableFrom(SummaryViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                SummaryViewModel(repository, prepareUseCase, GenerateDocumentSummaryUseCase(aiRepository!!)) as T
            }
            */
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
