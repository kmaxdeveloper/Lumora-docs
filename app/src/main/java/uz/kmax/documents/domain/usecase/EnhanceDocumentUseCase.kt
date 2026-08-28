package uz.kmax.documents.domain.usecase

import android.graphics.Bitmap
import uz.kmax.documents.data.processing.DocumentEnhancementProcessor
import uz.kmax.documents.domain.model.DocumentEnhancementMode

class EnhanceDocumentUseCase(private val processor: DocumentEnhancementProcessor) {
    suspend operator fun invoke(bitmap: Bitmap, mode: DocumentEnhancementMode): Bitmap {
        return processor.process(bitmap, mode)
    }
}
