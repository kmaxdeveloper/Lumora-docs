package uz.kmax.documents.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import uz.kmax.documents.domain.model.ocr.*
import java.io.File

class MlKitOcrDataSource(private val context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractText(imageFile: File): OcrResult? {
        if (!imageFile.exists()) return null
        
        val image = try {
            InputImage.fromFilePath(context, Uri.fromFile(imageFile))
        } catch (e: Exception) {
            return null
        }
        
        return try {
            val visionText = recognizer.process(image).await()
            OcrResult(
                fullText = visionText.text,
                blocks = visionText.textBlocks.map { block ->
                    OcrBlock(
                        text = block.text,
                        boundingBox = block.boundingBox,
                        lines = block.lines.map { line ->
                            OcrLine(
                                text = line.text,
                                boundingBox = line.boundingBox,
                                elements = line.elements.map { element ->
                                    OcrElement(
                                        text = element.text,
                                        boundingBox = element.boundingBox,
                                        confidence = element.confidence
                                    )
                                }
                            )
                        }
                    )
                }
            )
        } catch (e: Exception) {
            null
        }
    }
}
