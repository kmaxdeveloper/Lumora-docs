package uz.kmax.documents.domain.scanner

import android.graphics.PointF
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

class DocumentDetector {

    fun detect(mat: Mat): DocumentDetectionResult {
        val width = mat.width()
        val height = mat.height()

        // 1. Preprocessing
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

        val edged = Mat()
        Imgproc.Canny(blurred, edged, 75.0, 200.0)

        // 2. Find Contours
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edged, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        // 3. Filter Contours
        var maxArea = 0.0
        var bestContour: MatOfPoint2f? = null

        val minAreaThreshold = width * height * 0.1 // Minimum 10% of frame

        for (contour in contours) {
            val contour2f = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)

            if (approx.total() == 4L) {
                val area = Imgproc.contourArea(approx)
                if (area > minAreaThreshold && area > maxArea) {
                    maxArea = area
                    bestContour = approx
                }
            }
        }

        // 4. Cleanup
        gray.release()
        blurred.release()
        edged.release()
        hierarchy.release()
        contours.forEach { it.release() }

        if (bestContour != null) {
            val points = bestContour.toArray()
            bestContour.release()
            
            val orderedPoints = orderPoints(points)
            return DocumentDetectionResult(
                detected = true,
                corners = orderedPoints.map { PointF(it.x.toFloat(), it.y.toFloat()) },
                confidence = (maxArea / (width * height)).toFloat(),
                width = width,
                height = height
            )
        }

        return DocumentDetectionResult(detected = false, width = width, height = height)
    }

    private fun orderPoints(points: Array<Point>): Array<Point> {
        if (points.size != 4) return points

        // Robust sorting: 2 leftmost points, 2 rightmost points, then sort each by y
        val sortedByX = points.sortedBy { it.x }
        val leftPoints = sortedByX.take(2).sortedBy { it.y }
        val rightPoints = sortedByX.takeLast(2).sortedBy { it.y }

        val tl = leftPoints[0]
        val bl = leftPoints[1]
        val tr = rightPoints[0]
        val br = rightPoints[1]

        return arrayOf(tl, tr, br, bl)
    }
}
