package uz.kmax.documents.data.ai

import uz.kmax.documents.data.ai.dto.ChunkDto
import uz.kmax.documents.data.ai.dto.SummarizeRequest
import uz.kmax.documents.data.local.dao.DocumentDao
import uz.kmax.documents.data.local.entity.DocumentAiSummaryEntity
import uz.kmax.documents.domain.model.ai.AiSummary
import uz.kmax.documents.domain.model.ai.AiUsage
import uz.kmax.documents.domain.model.ai.DocumentContext
import uz.kmax.documents.domain.repository.AiRepository

class AiRepositoryImpl(
    private val apiService: AiApiService,
    private val dao: DocumentDao
) : AiRepository {

    override suspend fun getSummary(documentId: String): AiSummary? {
        return dao.getSummary(documentId)?.toDomain()
    }

    override suspend fun summarize(context: DocumentContext): Result<AiSummary> {
        val currentHash = context.normalizedText.hashCode()
        val cached = dao.getSummary(context.documentId)
        
        if (cached != null && cached.sourceTextHash == currentHash) {
            return Result.success(cached.toDomain())
        }

        return try {
            val request = SummarizeRequest(
                documentId = context.documentId,
                language = context.language ?: "UNKNOWN",
                text = context.normalizedText,
                chunks = context.chunks.map { ChunkDto(it.chunkIndex, it.text) }
            )
            val response = apiService.summarize(request)
            
            val summary = AiSummary(
                summary = response.summary,
                keyPoints = response.keyPoints,
                importantFacts = response.importantFacts,
                warnings = response.warnings,
                generatedAt = System.currentTimeMillis(),
                usage = AiUsage(
                    requestsToday = 1,
                    estimatedTokensToday = response.usage.inputTokens + response.usage.outputTokens
                )
            )
            
            dao.insertSummary(DocumentAiSummaryEntity.fromDomain(context.documentId, summary, currentHash))
            
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSummary(documentId: String) {
        dao.getSummary(documentId)?.let { dao.deleteSummary(it) }
    }
}
