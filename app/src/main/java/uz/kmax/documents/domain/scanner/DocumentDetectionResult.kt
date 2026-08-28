package uz.kmax.documents.domain.scanner

import android.graphics.PointF

data class DocumentDetectionResult(
    val detected: Boolean,
    val corners: List<PointF> = emptyList(),
    val confidence: Float = 0f,
    val width: Int = 0,
    val height: Int = 0
)
