package uz.kmax.documents.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import uz.kmax.documents.R
import uz.kmax.documents.domain.scanner.DocumentDetectionResult

class DocumentDetectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.accent)
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        pathEffect = CornerPathEffect(16f)
    }

    private val fillPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.accent)
        alpha = 40
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val stableColor = ContextCompat.getColor(context, R.color.success)
    private val defaultColor = ContextCompat.getColor(context, R.color.accent)

    private var detectionResult: DocumentDetectionResult? = null
    private var isStable: Boolean = false
    private val path = Path()

    fun setDetectionResult(result: DocumentDetectionResult?, isStable: Boolean = false) {
        this.detectionResult = result
        this.isStable = isStable
        
        val color = if (isStable) stableColor else defaultColor
        paint.color = color
        fillPaint.color = color
        fillPaint.alpha = if (isStable) 60 else 40
        
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val result = detectionResult
        
        if (result == null || !result.detected || result.corners.isEmpty()) {
            // Draw subtle guide if no document detected
            // (Optional: we already have viewGuide in layout, 
            // but we could draw something more dynamic here)
            return
        }

        path.reset()
        val corners = result.corners
        path.moveTo(corners[0].x, corners[0].y)
        path.lineTo(corners[1].x, corners[1].y)
        path.lineTo(corners[2].x, corners[2].y)
        path.lineTo(corners[3].x, corners[3].y)
        path.close()

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, paint)
    }
}
