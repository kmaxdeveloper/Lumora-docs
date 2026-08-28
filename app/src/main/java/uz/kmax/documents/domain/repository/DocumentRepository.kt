package uz.kmax.documents.domain.repository

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import uz.kmax.documents.domain.model.Document
import uz.kmax.documents.domain.model.DocumentPage

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>
    fun observeRecentDocuments(limit: Int): Flow<List<Document>>
    fun observeDocument(id: String): Flow<Document?>
    suspend fun getDocument(id: String): Document?
    
    suspend fun saveDocument(bitmap: Bitmap, name: String? = null): Document?
    suspend fun saveMultiPageDocument(imagePaths: List<String>, name: String? = null): Document?
    
    suspend fun updateDocument(document: Document)
    suspend fun deleteDocument(document: Document)
    suspend fun generatePdf(document: Document): Document?
    suspend fun deletePdf(document: Document): Document?
    suspend fun duplicateDocument(document: Document): Document?
    
    suspend fun getPages(documentId: String): List<DocumentPage>
    fun observePages(documentId: String): Flow<List<DocumentPage>>
    suspend fun updatePageOrder(pages: List<DocumentPage>)
    suspend fun deletePage(page: DocumentPage)
    
    suspend fun saveEnhancedImage(document: Document, bitmap: Bitmap): Document?
    suspend fun saveEnhancedPageImage(page: DocumentPage, bitmap: Bitmap): DocumentPage?
}
