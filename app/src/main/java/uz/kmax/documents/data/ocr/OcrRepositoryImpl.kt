package uz.kmax.documents.data.ocr

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import uz.kmax.documents.data.local.dao.DocumentPageDao
import uz.kmax.documents.data.local.entity.DocumentPageOcrEntity
import uz.kmax.documents.domain.model.DocumentPage
import uz.kmax.documents.domain.model.ocr.DocumentPageOcr
import uz.kmax.documents.domain.model.ocr.OcrResult
import uz.kmax.documents.domain.repository.DocumentRepository
import uz.kmax.documents.domain.repository.OcrRepository
import java.io.File

class OcrRepositoryImpl(
    private val dataSource: MlKitOcrDataSource,
    private val pageDao: DocumentPageDao,
    private val documentRepository: DocumentRepository
) : OcrRepository {

    override suspend fun extractPageText(page: DocumentPage): OcrResult? {
        val result = dataSource.extractText(File(page.activeImagePath))
        if (result != null) {
            val ocrEntity = DocumentPageOcrEntity(
                pageId = page.id,
                text = result.fullText,
                processedAt = result.timestamp,
                confidence = result.confidence
            )
            pageDao.insertPageOcr(ocrEntity)
        }
        return result
    }

    override fun observePageOcr(pageId: String): Flow<DocumentPageOcr?> {
        // This would need page index if we really wanted to use it in domain. 
        // For single page observation we might not need it or we can fetch page.
        return pageDao.getPageOcrFlow(pageId).map { it?.toDomain(0) }
    }

    override suspend fun getDocumentOcr(documentId: String): List<DocumentPageOcr> {
        val pageEntities = pageDao.getPagesByDocumentId(documentId).associateBy { it.id }
        val ocrEntities = pageDao.getOcrByDocumentId(documentId)
        
        return ocrEntities.map { ocr ->
            ocr.toDomain(pageEntities[ocr.pageId]?.pageIndex ?: 0)
        }.sortedBy { it.pageIndex }
    }

    override fun observeDocumentOcr(documentId: String): Flow<List<DocumentPageOcr>> {
        val pagesFlow = pageDao.observePagesByDocumentId(documentId)
        val ocrFlow = pageDao.observeOcrByDocumentId(documentId)
        
        return combine(pagesFlow, ocrFlow) { pageEntities, ocrEntities ->
            val pageMap = pageEntities.associateBy { it.id }
            ocrEntities.map { ocr ->
                ocr.toDomain(pageMap[ocr.pageId]?.pageIndex ?: 0)
            }.sortedBy { it.pageIndex }
        }
    }
}
