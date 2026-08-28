package uz.kmax.documents.data.ai

import uz.kmax.documents.domain.model.Document
import uz.kmax.documents.domain.model.ai.DocumentChunk
import uz.kmax.documents.domain.model.ai.DocumentContext

class DocumentContextBuilder(
    private val normalizer: DocumentTextNormalizer,
    private val maxChunkTokens: Int = 1000
) {

    fun build(document: Document): DocumentContext? {
        val rawText = document.ocrText ?: return null
        val normalizedText = normalizer.normalize(rawText)
        
        if (normalizedText.isBlank()) {
            return createEmptyContext(document)
        }

        val characterCount = normalizedText.length
        val wordCount = normalizedText.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val lineCount = normalizedText.count { it == '\n' } + 1
        val paragraphCount = normalizedText.split("\n\n").size
        val estimatedTotalTokens = estimateTokens(normalizedText)

        val chunks = createChunks(normalizedText)

        return DocumentContext(
            documentId = document.id,
            title = document.name,
            extractedText = rawText,
            normalizedText = normalizedText,
            language = "UNKNOWN", // Language detection not yet implemented
            characterCount = characterCount,
            wordCount = wordCount,
            lineCount = lineCount,
            paragraphCount = paragraphCount,
            estimatedTokenCount = estimatedTotalTokens,
            chunks = chunks
        )
    }

    private fun createEmptyContext(document: Document): DocumentContext {
        return DocumentContext(
            documentId = document.id,
            title = document.name,
            extractedText = "",
            normalizedText = "",
            characterCount = 0,
            wordCount = 0,
            lineCount = 0,
            paragraphCount = 0,
            estimatedTokenCount = 0,
            chunks = emptyList()
        )
    }

    private fun estimateTokens(text: String): Int {
        // Heuristic: ~4 characters per token
        return (text.length / 4.0).toInt() + 1
    }

    private fun createChunks(text: String): List<DocumentChunk> {
        val chunks = mutableListOf<DocumentChunk>()
        val paragraphs = text.split("\n\n")
        
        var currentChunkText = StringBuilder()
        var currentChunkTokenCount = 0
        var chunkIndex = 0

        for (paragraph in paragraphs) {
            val paragraphTokens = estimateTokens(paragraph)
            
            if (currentChunkTokenCount + paragraphTokens > maxChunkTokens && currentChunkText.isNotEmpty()) {
                // Save current chunk
                chunks.add(DocumentChunk(chunkIndex++, currentChunkText.toString().trim(), currentChunkTokenCount))
                currentChunkText = StringBuilder()
                currentChunkTokenCount = 0
            }

            if (paragraphTokens > maxChunkTokens) {
                // Paragraph itself is too large, split by lines or just hard cut (simplified for foundation)
                val lines = paragraph.split("\n")
                for (line in lines) {
                    val lineTokens = estimateTokens(line)
                    if (currentChunkTokenCount + lineTokens > maxChunkTokens && currentChunkText.isNotEmpty()) {
                        chunks.add(DocumentChunk(chunkIndex++, currentChunkText.toString().trim(), currentChunkTokenCount))
                        currentChunkText = StringBuilder()
                        currentChunkTokenCount = 0
                    }
                    currentChunkText.append(line).append("\n")
                    currentChunkTokenCount += lineTokens
                }
            } else {
                currentChunkText.append(paragraph).append("\n\n")
                currentChunkTokenCount += paragraphTokens
            }
        }

        if (currentChunkText.isNotEmpty()) {
            chunks.add(DocumentChunk(chunkIndex, currentChunkText.toString().trim(), currentChunkTokenCount))
        }

        return chunks
    }
}
