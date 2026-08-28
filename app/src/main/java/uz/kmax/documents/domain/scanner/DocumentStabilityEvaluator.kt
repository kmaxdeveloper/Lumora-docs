package uz.kmax.documents.domain.scanner

import android.graphics.PointF
import kotlin.math.sqrt

class DocumentStabilityEvaluator(
    private val historySize: Int = 10,
    private val movementThreshold: Float = 30f,
    private val minAreaRatio: Float = 0.15f
) {
    private val history = mutableListOf<List<PointF>>()

    fun evaluate(result: DocumentDetectionResult): StabilityResult {
        if (!result.detected || result.corners.size != 4) {
            history.clear()
            return StabilityResult.NOT_DETECTED
        }

        // 1. Area validation
        val area = calculateArea(result.corners)
        val frameArea = (result.width * result.height).toFloat()
        if (area / frameArea < minAreaRatio) {
            history.clear()
            return StabilityResult.TOO_SMALL
        }

        // 2. Stability validation
        history.add(result.corners)
        if (history.size > historySize) {
            history.removeAt(0)
        }

        if (history.size < historySize) {
            return StabilityResult.STABILIZING
        }

        val isStable = checkStability()
        return if (isStable) {
            StabilityResult.STABLE
        } else {
            StabilityResult.MOVING
        }
    }

    private fun checkStability(): Boolean {
        if (history.size < 2) return false
        
        var totalMovement = 0f
        val latest = history.last()
        
        for (i in 0 until history.size - 1) {
            val prev = history[i]
            for (j in 0 until 4) {
                totalMovement += distance(latest[j], prev[j])
            }
        }
        
        val averageMovement = totalMovement / (history.size * 4)
        return averageMovement < movementThreshold
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun calculateArea(corners: List<PointF>): Float {
        // Shoelace formula
        var area = 0f
        for (i in corners.indices) {
            val j = (i + 1) % corners.size
            area += corners[i].x * corners[j].y
            area -= corners[j].x * corners[i].y
        }
        return Math.abs(area) / 2f
    }

    fun reset() {
        history.clear()
    }
}

enum class StabilityResult {
    NOT_DETECTED,
    TOO_SMALL,
    STABILIZING,
    MOVING,
    STABLE
}
