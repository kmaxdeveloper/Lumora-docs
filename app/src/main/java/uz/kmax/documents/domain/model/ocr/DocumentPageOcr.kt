package uz.kmax.documents.domain.model.ocr

data class DocumentPageOcr(
    val pageId: String,
    val pageIndex: Int,
    val text: String,
    val processedAt: Long,
    val confidence: Float? = null
)
