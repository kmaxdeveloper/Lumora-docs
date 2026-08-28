package uz.kmax.documents.domain.model.ai

data class AiUsage(
    val requestsToday: Int,
    val estimatedTokensToday: Int
)
