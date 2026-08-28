package uz.kmax.documents.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import org.opencv.core.Point

/**
 * Utility for handling camera sensor orientation vs EXIF display orientation.
 *
 * CameraX saves JPEGs in sensor-native orientation (often landscape on phones) and embeds
 * an EXIF rotation tag to tell viewers how to rotate for correct display.
 * BitmapFactory.decodeFile ignores EXIF, so manual correction is required.
 *
 * Coordinate spaces:
 *  - "sensor space"  : the raw bitmap space (matches analysis frame, matches JPEG pixel data)
 *  - "display space" : sensor space rotated by EXIF degrees (what the user sees on screen)
 */
object BitmapOrientationHelper {

    /**
     * Reads EXIF rotation degrees (0, 90, 180, or 270) from a JPEG file path.
     * Returns 0 if the EXIF tag is absent or unreadable.
     */
    fun readExifRotation(path: String): Int {
        return try {
            val exif = ExifInterface(path)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else                                  -> 0
            }
        } catch (e: Exception) {
            Log.w("BitmapOrientation", "Could not read EXIF from $path", e)
            0
        }
    }

    /**
     * Rotates [bitmap] by [degrees] clockwise.
     * The input bitmap is recycled if rotation is applied; returns the same instance for 0°.
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return rotated
    }

    /**
     * Transforms a point from SENSOR space to DISPLAY (EXIF-corrected) space.
     *
     * [sensorW] / [sensorH] are the raw bitmap dimensions (same as the analysis frame).
     * [rotation] is the EXIF rotation in degrees (0, 90, 180, 270).
     *
     * The returned [Point] is in display space, whose size is given by [displaySize].
     */
    fun sensorToDisplay(cx: Double, cy: Double, sensorW: Int, sensorH: Int, rotation: Int): Point {
        return when (rotation) {
            90  -> Point(sensorH - cy, cx)
            180 -> Point(sensorW - cx, sensorH - cy)
            270 -> Point(cy, sensorW - cx)
            else -> Point(cx, cy)
        }
    }

    /**
     * Transforms a point from DISPLAY (EXIF-corrected) space back to SENSOR (raw) space.
     * This is the exact inverse of [sensorToDisplay].
     *
     * [sensorW] / [sensorH] are the raw bitmap dimensions.
     * [rotation] is the EXIF rotation in degrees (0, 90, 180, 270).
     */
    fun displayToSensor(dx: Double, dy: Double, sensorW: Int, sensorH: Int, rotation: Int): Point {
        return when (rotation) {
            90  -> Point(dy, sensorH - dx)
            180 -> Point(sensorW - dx, sensorH - dy)
            270 -> Point(sensorW - dy, dx)
            else -> Point(dx, dy)
        }
    }

    /**
     * Returns the (width, height) of the EXIF-corrected bitmap
     * given the original sensor dimensions and EXIF rotation.
     */
    fun displaySize(sensorW: Int, sensorH: Int, rotation: Int): Pair<Int, Int> =
        if (rotation == 90 || rotation == 270) Pair(sensorH, sensorW) else Pair(sensorW, sensorH)

    /**
     * Robust geometric ordering of 4 points in display space into [TL, TR, BR, BL] sequence.
     * Uses sum (x + y) and diff (y - x) properties:
     *  - TL has smallest (x + y)
     *  - BR has largest (x + y)
     *  - TR has smallest (y - x)
     *  - BL has largest (y - x)
     */
    fun orderDisplayCorners(pts: List<Point>): List<Point> {
        if (pts.size != 4) return pts
        val tl = pts.minByOrNull { it.x + it.y } ?: pts[0]
        val br = pts.maxByOrNull { it.x + it.y } ?: pts[2]
        val tr = pts.minByOrNull { it.y - it.x } ?: pts[1]
        val bl = pts.maxByOrNull { it.y - it.x } ?: pts[3]
        return listOf(tl, tr, br, bl)
    }
}

