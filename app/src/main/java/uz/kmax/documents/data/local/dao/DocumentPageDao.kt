package uz.kmax.documents.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import uz.kmax.documents.data.local.entity.DocumentPageEntity
import uz.kmax.documents.data.local.entity.DocumentPageOcrEntity

@Dao
interface DocumentPageDao {
    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    fun observePagesByDocumentId(documentId: String): Flow<List<DocumentPageEntity>>

    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getPagesByDocumentId(documentId: String): List<DocumentPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: DocumentPageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<DocumentPageEntity>)

    @Update
    suspend fun update(page: DocumentPageEntity)

    @Delete
    suspend fun delete(page: DocumentPageEntity)

    @Query("DELETE FROM document_pages WHERE documentId = :documentId")
    suspend fun deleteByDocumentId(documentId: String)

    @Query("SELECT * FROM page_ocr WHERE pageId = :pageId")
    suspend fun getPageOcr(pageId: String): DocumentPageOcrEntity?

    @Query("SELECT * FROM page_ocr WHERE pageId = :pageId")
    fun getPageOcrFlow(pageId: String): Flow<DocumentPageOcrEntity?>

    @Query("SELECT * FROM page_ocr WHERE pageId IN (SELECT id FROM document_pages WHERE documentId = :documentId)")
    suspend fun getOcrByDocumentId(documentId: String): List<DocumentPageOcrEntity>

    @Query("SELECT * FROM page_ocr WHERE pageId IN (SELECT id FROM document_pages WHERE documentId = :documentId)")
    fun observeOcrByDocumentId(documentId: String): Flow<List<DocumentPageOcrEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPageOcr(ocr: DocumentPageOcrEntity)
}
