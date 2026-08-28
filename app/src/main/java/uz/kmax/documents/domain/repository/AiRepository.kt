package uz.kmax.documents.domain.repository

import uz.kmax.documents.domain.model.ai.AiSummary
import uz.kmax.documents.domain.model.ai.DocumentContext

interface AiRepository {
    suspend fun getSummary(documentId: String): AiSummary?
    suspend fun summarize(context: DocumentContext): Result<AiSummary>
    suspend fun deleteSummary(documentId: String)
}
