package uz.kmax.documents.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import uz.kmax.documents.data.local.entity.DocumentAiSummaryEntity
import uz.kmax.documents.data.local.entity.DocumentEntity

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeById(id: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity)

    @Update
    suspend fun update(document: DocumentEntity)

    @Delete
    suspend fun delete(document: DocumentEntity)

    @Query("SELECT * FROM document_summaries WHERE documentId = :documentId")
    suspend fun getSummary(documentId: String): DocumentAiSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: DocumentAiSummaryEntity)

    @Delete
    suspend fun deleteSummary(summary: DocumentAiSummaryEntity)
}
