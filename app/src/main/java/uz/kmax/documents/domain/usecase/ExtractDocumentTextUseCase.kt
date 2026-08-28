package uz.kmax.documents.domain.usecase

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import uz.kmax.documents.domain.model.Document
import uz.kmax.documents.domain.model.DocumentPage
import uz.kmax.documents.domain.repository.OcrRepository

class ExtractDocumentTextUseCase(private val repository: OcrRepository) {
    suspend operator fun invoke(
        document: Document, 
        forceRedo: Boolean = false, 
        onProgress: (Int, Int) -> Unit
    ): Boolean {
        val pages = document.pages
        if (pages.isEmpty()) {
            // Support backward compatibility
            return repository.extractPageText(DocumentPage(
                id = document.id + "_page0",
                documentId = document.id,
                pageIndex = 0,
                imagePath = document.imagePath,
                enhancedImagePath = document.enhancedImagePath,
                createdAt = document.createdAt
            )) != null
        }

        var successCount = 0
        for (index in pages.indices) {
            if (!currentCoroutineContext().isActive) break
            
            val page = pages[index]
            if (!forceRedo && page.hasOcr) {
                successCount++
                onProgress(index + 1, pages.size)
                continue
            }

            onProgress(index + 1, pages.size)
            val result = repository.extractPageText(page)
            if (result != null) successCount++
        }
        
        return successCount > 0
    }
}
