package uz.kmax.documents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import uz.kmax.documents.domain.model.ai.AiSummary
import uz.kmax.documents.domain.model.ai.AiUsage

@Entity(
    tableName = "document_summaries",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId"])]
)
data class DocumentAiSummaryEntity(
    @PrimaryKey val documentId: String,
    val summary: String,
    val keyPoints: String, // Stored as newline separated or JSON
    val importantFacts: String,
    val warnings: String,
    val generatedAt: Long,
    val sourceTextHash: Int,
    val inputTokens: Int,
    val outputTokens: Int
) {
    fun toDomain() = AiSummary(
        summary = summary,
        keyPoints = if (keyPoints.isBlank()) emptyList() else keyPoints.split("\n"),
        importantFacts = if (importantFacts.isBlank()) emptyList() else importantFacts.split("\n"),
        warnings = if (warnings.isBlank()) emptyList() else warnings.split("\n"),
        generatedAt = generatedAt,
        usage = AiUsage(1, inputTokens + outputTokens)
    )

    companion object {
        fun fromDomain(documentId: String, summary: AiSummary, sourceHash: Int) = DocumentAiSummaryEntity(
            documentId = documentId,
            summary = summary.summary,
            keyPoints = summary.keyPoints.joinToString("\n"),
            importantFacts = summary.importantFacts.joinToString("\n"),
            warnings = summary.warnings.joinToString("\n"),
            generatedAt = summary.generatedAt,
            sourceTextHash = sourceHash,
            inputTokens = 0, // In production, usage would be more detailed
            outputTokens = summary.usage.estimatedTokensToday
        )
    }
}
