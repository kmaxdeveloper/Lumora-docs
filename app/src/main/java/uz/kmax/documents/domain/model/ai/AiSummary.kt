package uz.kmax.documents.domain.model.ai

data class AiSummary(
    val summary: String,
    val keyPoints: List<String>,
    val importantFacts: List<String>,
    val warnings: List<String>,
    val generatedAt: Long,
    val usage: AiUsage
)
