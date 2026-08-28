package uz.kmax.documents.domain.model.ai

data class DocumentChunk(
    val chunkIndex: Int,
    val text: String,
    val estimatedTokenCount: Int
)
