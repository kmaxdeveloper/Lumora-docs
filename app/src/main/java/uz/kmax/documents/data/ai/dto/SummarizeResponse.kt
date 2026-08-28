package uz.kmax.documents.data.ai.dto

data class SummarizeResponse(
    val summary: String,
    val keyPoints: List<String>,
    val importantFacts: List<String>,
    val warnings: List<String>,
    val language: String,
    val usage: UsageDto
)

data class UsageDto(
    val inputTokens: Int,
    val outputTokens: Int
)
