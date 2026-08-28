package uz.kmax.documents.domain.model

data class Document(
    val id: String,
    val name: String,
    val imagePath: String, // Keeping for backward compatibility and as cover
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val fileSize: Long,
    val pdfPath: String? = null,
    val pdfSize: Long = 0L,
    val enhancedImagePath: String? = null,
    val ocrText: String? = null, // Old single-page OCR or combined cache
    val ocrTimestamp: Long? = null,
    val pages: List<DocumentPage> = emptyList()
) {
    val activeImagePath: String get() = enhancedImagePath ?: imagePath
    val hasOcr: Boolean get() = ocrText != null || pages.any { it.hasOcr }
}
