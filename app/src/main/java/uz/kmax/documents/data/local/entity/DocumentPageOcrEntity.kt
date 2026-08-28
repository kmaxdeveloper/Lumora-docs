package uz.kmax.documents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import uz.kmax.documents.domain.model.ocr.DocumentPageOcr

@Entity(
    tableName = "page_ocr",
    foreignKeys = [
        ForeignKey(
            entity = DocumentPageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["pageId"])]
)
data class DocumentPageOcrEntity(
    @PrimaryKey val pageId: String,
    val text: String,
    val processedAt: Long,
    val confidence: Float?
) {
    fun toDomain(pageIndex: Int) = DocumentPageOcr(
        pageId = pageId,
        pageIndex = pageIndex,
        text = text,
        processedAt = processedAt,
        confidence = confidence
    )

    companion object {
        fun fromDomain(ocr: DocumentPageOcr) = DocumentPageOcrEntity(
            pageId = ocr.pageId,
            text = ocr.text,
            processedAt = ocr.processedAt,
            confidence = ocr.confidence
        )
    }
}
