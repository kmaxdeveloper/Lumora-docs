package uz.kmax.documents.domain.model.ocr

import android.graphics.Rect

data class OcrResult(
    val fullText: String,
    val blocks: List<OcrBlock>,
    val confidence: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class OcrBlock(
    val text: String,
    val boundingBox: Rect?,
    val lines: List<OcrLine>
)

data class OcrLine(
    val text: String,
    val boundingBox: Rect?,
    val elements: List<OcrElement>
)

data class OcrElement(
    val text: String,
    val boundingBox: Rect?,
    val confidence: Float?
)
