package uz.kmax.documents.domain.model.ai

data class DocumentContext(
    val documentId: String,
    val title: String,
    val extractedText: String,
    val normalizedText: String,
    val language: String? = null,
    val characterCount: Int,
    val wordCount: Int,
    val lineCount: Int,
    val paragraphCount: Int,
    val estimatedTokenCount: Int,
    val chunks: List<DocumentChunk>
)
