package uz.kmax.documents.data.ai.dto

data class SummarizeRequest(
    val documentId: String,
    val language: String,
    val text: String,
    val chunks: List<ChunkDto>
)

data class ChunkDto(
    val index: Int,
    val text: String
)
