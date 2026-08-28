package uz.kmax.documents.domain.scanner

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Perspective correction processor using OpenCV warpPerspective.
 *
 * Corrects perspective foreshortening so that angled documents
 * are unwarped into flat, perfectly proportioned images without
 * squishing or unnatural distortion.
 *
 * Corners order:
 *  0: Top-Left (TL)
 *  1: Top-Right (TR)
 *  2: Bottom-Right (BR)
 *  3: Bottom-Left (BL)
 */
class PerspectiveCorrectionProcessor {

    fun process(bitmap: Bitmap, corners: List<Point>): Bitmap? {
        if (corners.size != 4) return null

        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val tl = corners[0]
        val tr = corners[1]
        val br = corners[2]
        val bl = corners[3]

        // 1. Edge lengths of the quadrilateral
        val topW = dist(tl, tr)
        val bottomW = dist(bl, br)
        val leftH = dist(tl, bl)
        val rightH = dist(tr, br)

        val maxW = max(topW, bottomW)
        val minW = min(topW, bottomW).coerceAtLeast(1.0)

        val maxH = max(leftH, rightH)
        val minH = min(leftH, rightH).coerceAtLeast(1.0)

        val avgW = (topW + bottomW) / 2.0
        val avgH = (leftH + rightH) / 2.0

        // 2. Perspective foreshortening compensation:
        // When a rectangular document is tilted towards the camera,
        // the receding dimension is compressed by cos(theta) = minDim / maxDim.
        // We restore the true physical aspect ratio:
        val widthRatio = maxW / minW
        val heightRatio = maxH / minH

        // Compensate foreshortened height if width is trapezoidal, or foreshortened width if height is trapezoidal
        var targetW = max(maxW, avgW * heightRatio)
        var targetH = max(maxH, avgH * widthRatio)

        // Safety clamp: keep within reasonable bitmap dimensions (10px .. 8000px)
        val outWidth = targetW.toInt().coerceIn(10, 8000)
        val outHeight = targetH.toInt().coerceIn(10, 8000)

        // 3. Source and destination points
        val srcPoints = MatOfPoint2f(tl, tr, br, bl)
        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(outWidth.toDouble(), 0.0),
            Point(outWidth.toDouble(), outHeight.toDouble()),
            Point(0.0, outHeight.toDouble())
        )

        // 4. Compute homography matrix and warp
        val perspectiveTransform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
        val resultMat = Mat(outHeight, outWidth, CvType.CV_8UC4)
        Imgproc.warpPerspective(
            mat,
            resultMat,
            perspectiveTransform,
            Size(outWidth.toDouble(), outHeight.toDouble()),
            Imgproc.INTER_CUBIC
        )

        // 5. Output bitmap
        val resultBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(resultMat, resultBitmap)

        // Cleanup
        mat.release()
        resultMat.release()
        srcPoints.release()
        dstPoints.release()
        perspectiveTransform.release()

        return resultBitmap
    }

    private fun dist(a: Point, b: Point): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}
