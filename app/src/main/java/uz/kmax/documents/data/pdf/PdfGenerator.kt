package uz.kmax.documents.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfGenerator(private val context: Context) {

    // A4 dimensions at 72 DPI
    private val A4_WIDTH = 595
    private val A4_HEIGHT = 842

    suspend fun generate(imageFile: File, outputFile: File): Boolean {
        return generateMultiple(listOf(imageFile), outputFile)
    }

    suspend fun generateMultiple(imageFiles: List<File>, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        
        var renderedPages = 0
        try {
            imageFiles.forEach { file ->
                if (!file.exists()) return@forEach
                
                // Decode with sample size if image is too large for a single PDF page
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
                
                // For PDF we don't need full 4K resolution per page, 1500-2000px is usually enough for A4
                val maxDim = 2000
                var sampleSize = 1
                if (options.outWidth > maxDim || options.outHeight > maxDim) {
                    sampleSize = 2
                }
                
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return@forEach
                
                try {
                    val scale = minOf(
                        A4_WIDTH.toFloat() / bitmap.width,
                        A4_HEIGHT.toFloat() / bitmap.height
                    )
                    
                    val scaledWidth = (bitmap.width * scale).toInt()
                    val scaledHeight = (bitmap.height * scale).toInt()
                    
                    val left = (A4_WIDTH - scaledWidth) / 2
                    val top = (A4_HEIGHT - scaledHeight) / 2
                    
                    val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, renderedPages + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    
                    val canvas = page.canvas
                    val paint = Paint().apply {
                        isFilterBitmap = true
                        isAntiAlias = true
                    }
                    
                    val destRect = android.graphics.Rect(left, top, left + scaledWidth, top + scaledHeight)
                    canvas.drawBitmap(bitmap, null, destRect, paint)
                    
                    pdfDocument.finishPage(page)
                    renderedPages++
                } finally {
                    bitmap.recycle()
                }
            }
            
            if (renderedPages == 0) {
                return@withContext false
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            
            true
        } catch (e: Exception) {
            if (outputFile.exists()) outputFile.delete()
            false
        } finally {
            pdfDocument.close()
        }
    }
}
