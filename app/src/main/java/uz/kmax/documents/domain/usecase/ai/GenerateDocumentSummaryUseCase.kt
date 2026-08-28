package uz.kmax.documents.domain.usecase.ai

import uz.kmax.documents.domain.model.ai.AiSummary
import uz.kmax.documents.domain.model.ai.DocumentContext
import uz.kmax.documents.domain.repository.AiRepository

class GenerateDocumentSummaryUseCase(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(context: DocumentContext): Result<AiSummary> {
        return aiRepository.summarize(context)
    }
}
