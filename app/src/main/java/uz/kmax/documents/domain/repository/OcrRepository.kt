package uz.kmax.documents.domain.repository

import kotlinx.coroutines.flow.Flow
import uz.kmax.documents.domain.model.Document
import uz.kmax.documents.domain.model.DocumentPage
import uz.kmax.documents.domain.model.ocr.DocumentPageOcr
import uz.kmax.documents.domain.model.ocr.OcrResult

interface OcrRepository {
    suspend fun extractPageText(page: DocumentPage): OcrResult?
    fun observePageOcr(pageId: String): Flow<DocumentPageOcr?>
    suspend fun getDocumentOcr(documentId: String): List<DocumentPageOcr>
    fun observeDocumentOcr(documentId: String): Flow<List<DocumentPageOcr>>
}
