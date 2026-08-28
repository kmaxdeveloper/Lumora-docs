package uz.kmax.documents.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object BitmapUtils {

    suspend fun decodeUri(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri).use { 
                BitmapFactory.decodeStream(it, null, options)
            }

            var scale = 1
            if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
                scale = Math.pow(
                    2.0, 
                    Math.ceil(Math.log(maxOf(options.outWidth, options.outHeight) / maxDimension.toDouble()) / Math.log(2.0))
                ).toInt()
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
            }

            val bitmap = context.contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext null

            val rotation = getRotation(context, uri)
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                bitmap.recycle()
                return@withContext rotated
            }

            return@withContext bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun getRotation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { 
                val exif = ExifInterface(it)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
