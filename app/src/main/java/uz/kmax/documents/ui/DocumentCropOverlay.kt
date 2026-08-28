package uz.kmax.documents.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import uz.kmax.documents.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Strict Rectangular Crop Overlay View.
 *
 * Always maintains a true 90-degree upright rectangle [left, top, right, bottom].
 * Supports:
 *  - 4 Corner handles (TL, TR, BR, BL)
 *  - 4 Mid-edge handles (Top, Right, Bottom, Left)
 *  - Center dragging (moves the entire crop box)
 *  - Darkened scrim outside the crop rectangle
 *  - 3x3 Rule-of-thirds grid
 *  - Minimum dimension protection & bounds clamping
 */
class DocumentCropOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density
    private val accentColor = ContextCompat.getColor(context, R.color.accent)

    // Rectangle bounds in view coordinates
    private var cropLeft = 0f
    private var cropTop = 0f
    private var cropRight = 0f
    private var cropBottom = 0f

    private val minCropSize = 48f * density
    private val touchThreshold = 36f * density
    private val cornerHandleRadius = 13f * density
    private val cornerHandleOuterRadius = 14.5f * density
    private val activeGlowRadius = 24f * density
    private val edgeHandleLength = 24f * density
    private val edgeHandleThickness = 4f * density

    // Paints
    private val scrimPaint = Paint().apply {
        color = Color.BLACK
        alpha = 130
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.WHITE
        alpha = 90
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        isAntiAlias = true
    }

    private val cornerPaint = Paint().apply {
        color = accentColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val cornerOuterPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        isAntiAlias = true
    }

    private val edgeBarPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val activeGlowPaint = Paint().apply {
        color = accentColor
        alpha = 75
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Touch interaction tracking
    // 0: TL, 1: TR, 2: BR, 3: BL
    // 4: Top, 5: Right, 6: Bottom, 7: Left
    // 8: Center (move whole box)
    private var selectedTarget = -1
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var startLeft = 0f
    private var startTop = 0f
    private var startRight = 0f
    private var startBottom = 0f

    private var onCornersChanged: ((List<PointF>) -> Unit)? = null

    /**
     * Sets the crop rectangle. If 4 points are passed, takes their bounding box.
     */
    fun setCorners(newCorners: List<PointF>) {
        if (newCorners.isNotEmpty()) {
            val minX = newCorners.minOf { it.x }
            val maxX = newCorners.maxOf { it.x }
            val minY = newCorners.minOf { it.y }
            val maxY = newCorners.maxOf { it.y }

            cropLeft = minX
            cropTop = minY
            cropRight = maxX
            cropBottom = maxY
            invalidate()
        }
    }

    fun setRect(left: Float, top: Float, right: Float, bottom: Float) {
        cropLeft = left
        cropTop = top
        cropRight = right
        cropBottom = bottom
        invalidate()
    }

    /**
     * Returns the 4 corners of the strict rectangle in clockwise order:
     * 0: TL, 1: TR, 2: BR, 3: BL
     */
    fun getCorners(): List<PointF> {
        return listOf(
            PointF(cropLeft, cropTop),
            PointF(cropRight, cropTop),
            PointF(cropRight, cropBottom),
            PointF(cropLeft, cropBottom)
        )
    }

    fun setOnCornersChangedListener(listener: (List<PointF>) -> Unit) {
        this.onCornersChanged = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Default bounds if not set
        if (cropRight <= cropLeft || cropBottom <= cropTop) {
            val padX = w * 0.05f
            val padY = h * 0.05f
            cropLeft = padX
            cropTop = padY
            cropRight = w - padX
            cropBottom = h - padY
        }

        val l = cropLeft
        val t = cropTop
        val r = cropRight
        val b = cropBottom

        // 1. Darkened scrim outside the crop rectangle (4 rects)
        canvas.drawRect(0f, 0f, w, t, scrimPaint)          // Top
        canvas.drawRect(0f, b, w, h, scrimPaint)          // Bottom
        canvas.drawRect(0f, t, l, b, scrimPaint)          // Left
        canvas.drawRect(r, t, w, b, scrimPaint)          // Right

        // 2. White crop rectangle border
        canvas.drawRect(l, t, r, b, borderPaint)

        // 3. 3x3 Rule-of-thirds grid
        val gridW = (r - l) / 3f
        val gridH = (b - t) / 3f
        canvas.drawLine(l + gridW, t, l + gridW, b, gridPaint)
        canvas.drawLine(l + gridW * 2f, t, l + gridW * 2f, b, gridPaint)
        canvas.drawLine(l, t + gridH, r, t + gridH, gridPaint)
        canvas.drawLine(l, t + gridH * 2f, r, t + gridH * 2f, gridPaint)

        // 4. Mid-edge bar handles
        drawEdgeBar(canvas, (l + r) / 2f, t, isHorizontal = true, selectedTarget == 4)  // Top
        drawEdgeBar(canvas, r, (t + b) / 2f, isHorizontal = false, selectedTarget == 5) // Right
        drawEdgeBar(canvas, (l + r) / 2f, b, isHorizontal = true, selectedTarget == 6)  // Bottom
        drawEdgeBar(canvas, l, (t + b) / 2f, isHorizontal = false, selectedTarget == 7) // Left

        // 5. Corner handles
        drawCornerHandle(canvas, l, t, selectedTarget == 0) // TL
        drawCornerHandle(canvas, r, t, selectedTarget == 1) // TR
        drawCornerHandle(canvas, r, b, selectedTarget == 2) // BR
        drawCornerHandle(canvas, l, b, selectedTarget == 3) // BL
    }

    private fun drawCornerHandle(canvas: Canvas, x: Float, y: Float, isActive: Boolean) {
        if (isActive) {
            canvas.drawCircle(x, y, activeGlowRadius, activeGlowPaint)
        }
        canvas.drawCircle(x, y, cornerHandleRadius, cornerPaint)
        canvas.drawCircle(x, y, cornerHandleOuterRadius, cornerOuterPaint)
    }

    private fun drawEdgeBar(canvas: Canvas, x: Float, y: Float, isHorizontal: Boolean, isActive: Boolean) {
        if (isActive) {
            canvas.drawCircle(x, y, activeGlowRadius, activeGlowPaint)
        }
        val halfLen = edgeHandleLength / 2f
        val halfThick = edgeHandleThickness / 2f

        val rect = if (isHorizontal) {
            RectF(x - halfLen, y - halfThick, x + halfLen, y + halfThick)
        } else {
            RectF(x - halfThick, y - halfLen, x + halfThick, y + halfLen)
        }
        canvas.drawRoundRect(rect, halfThick, halfThick, edgeBarPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                selectedTarget = findHitTarget(event.x, event.y)
                if (selectedTarget != -1) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    initialTouchX = event.x
                    initialTouchY = event.y
                    startLeft = cropLeft
                    startTop = cropTop
                    startRight = cropRight
                    startBottom = cropBottom
                    invalidate()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (selectedTarget != -1) {
                    val deltaX = event.x - initialTouchX
                    val deltaY = event.y - initialTouchY
                    val maxW = width.toFloat()
                    val maxH = height.toFloat()

                    when (selectedTarget) {
                        0 -> { // Top-Left: moves left and top
                            cropLeft = (startLeft + deltaX).coerceIn(0f, startRight - minCropSize)
                            cropTop = (startTop + deltaY).coerceIn(0f, startBottom - minCropSize)
                        }
                        1 -> { // Top-Right: moves right and top
                            cropRight = (startRight + deltaX).coerceIn(startLeft + minCropSize, maxW)
                            cropTop = (startTop + deltaY).coerceIn(0f, startBottom - minCropSize)
                        }
                        2 -> { // Bottom-Right: moves right and bottom
                            cropRight = (startRight + deltaX).coerceIn(startLeft + minCropSize, maxW)
                            cropBottom = (startBottom + deltaY).coerceIn(startTop + minCropSize, maxH)
                        }
                        3 -> { // Bottom-Left: moves left and bottom
                            cropLeft = (startLeft + deltaX).coerceIn(0f, startRight - minCropSize)
                            cropBottom = (startBottom + deltaY).coerceIn(startTop + minCropSize, maxH)
                        }
                        4 -> { // Top edge: moves top
                            cropTop = (startTop + deltaY).coerceIn(0f, startBottom - minCropSize)
                        }
                        5 -> { // Right edge: moves right
                            cropRight = (startRight + deltaX).coerceIn(startLeft + minCropSize, maxW)
                        }
                        6 -> { // Bottom edge: moves bottom
                            cropBottom = (startBottom + deltaY).coerceIn(startTop + minCropSize, maxH)
                        }
                        7 -> { // Left edge: moves left
                            cropLeft = (startLeft + deltaX).coerceIn(0f, startRight - minCropSize)
                        }
                        8 -> { // Center: moves entire box
                            val boxW = startRight - startLeft
                            val boxH = startBottom - startTop
                            var newL = startLeft + deltaX
                            var newT = startTop + deltaY

                            if (newL < 0f) newL = 0f
                            if (newL + boxW > maxW) newL = maxW - boxW

                            if (newT < 0f) newT = 0f
                            if (newT + boxH > maxH) newT = maxH - boxH

                            cropLeft = newL
                            cropTop = newT
                            cropRight = newL + boxW
                            cropBottom = newT + boxH
                        }
                    }
                    invalidate()
                    onCornersChanged?.invoke(getCorners())
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (selectedTarget != -1) {
                    performClick()
                    selectedTarget = -1
                    invalidate()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun findHitTarget(x: Float, y: Float): Int {
        val l = cropLeft
        val t = cropTop
        val r = cropRight
        val b = cropBottom

        // 1. Check 4 corners (highest priority)
        val corners = listOf(
            PointF(l, t), // 0: TL
            PointF(r, t), // 1: TR
            PointF(r, b), // 2: BR
            PointF(l, b)  // 3: BL
        )
        for (i in corners.indices) {
            if (dist(x, y, corners[i].x, corners[i].y) < touchThreshold) {
                return i
            }
        }

        // 2. Check 4 edges
        val edgeMids = listOf(
            PointF((l + r) / 2f, t), // 4: Top
            PointF(r, (t + b) / 2f), // 5: Right
            PointF((l + r) / 2f, b), // 6: Bottom
            PointF(l, (t + b) / 2f)  // 7: Left
        )
        for (i in edgeMids.indices) {
            if (dist(x, y, edgeMids[i].x, edgeMids[i].y) < touchThreshold) {
                return 4 + i
            }
        }

        // 3. Check inside box for moving entire crop area
        if (x in l..r && y in t..b) {
            return 8
        }

        return -1
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }
}
