package uz.kmax.documents.data.processing

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import uz.kmax.documents.domain.model.DocumentEnhancementMode

class DocumentEnhancementProcessor {

    suspend fun process(source: Bitmap, mode: DocumentEnhancementMode): Bitmap = withContext(Dispatchers.Default) {
        if (mode == DocumentEnhancementMode.ORIGINAL) return@withContext source

        val mat = Mat()
        val resultMat = Mat()
        
        try {
            Utils.bitmapToMat(source, mat)

            when (mode) {
                DocumentEnhancementMode.AUTO -> {
                    Imgproc.cvtColor(mat, resultMat, Imgproc.COLOR_RGBA2GRAY)
                    Imgproc.GaussianBlur(resultMat, resultMat, Size(3.0, 3.0), 0.0)
                    Imgproc.equalizeHist(resultMat, resultMat)
                    Imgproc.cvtColor(resultMat, resultMat, Imgproc.COLOR_GRAY2RGBA)
                }
                DocumentEnhancementMode.GRAYSCALE -> {
                    Imgproc.cvtColor(mat, resultMat, Imgproc.COLOR_RGBA2GRAY)
                    Imgproc.equalizeHist(resultMat, resultMat)
                    Imgproc.cvtColor(resultMat, resultMat, Imgproc.COLOR_GRAY2RGBA)
                }
                DocumentEnhancementMode.BLACK_AND_WHITE -> {
                    val gray = Mat()
                    try {
                        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
                        Imgproc.adaptiveThreshold(
                            gray, resultMat, 255.0,
                            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                            Imgproc.THRESH_BINARY, 11, 2.0
                        )
                        Imgproc.cvtColor(resultMat, resultMat, Imgproc.COLOR_GRAY2RGBA)
                    } finally {
                        gray.release()
                    }
                }
                else -> {
                    mat.copyTo(resultMat)
                }
            }

            val resultBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultMat, resultBitmap)
            return@withContext resultBitmap
        } finally {
            mat.release()
            resultMat.release()
        }
    }
}
