package uz.kmax.documents.domain.model

data class DocumentPage(
    val id: String,
    val documentId: String,
    val pageIndex: Int,
    val imagePath: String,
    val enhancedImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val ocrText: String? = null,
    val ocrTimestamp: Long? = null
) {
    val activeImagePath: String get() = enhancedImagePath ?: imagePath
    val hasOcr: Boolean get() = ocrText != null
}
