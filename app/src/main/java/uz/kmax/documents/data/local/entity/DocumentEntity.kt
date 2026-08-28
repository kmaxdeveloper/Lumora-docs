package uz.kmax.documents.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import uz.kmax.documents.domain.model.Document

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val imagePath: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val fileSize: Long,
    val pdfPath: String? = null,
    val pdfSize: Long = 0L,
    val enhancedImagePath: String? = null,
    val ocrText: String? = null,
    val ocrTimestamp: Long? = null
) {
    fun toDomain() = Document(
        id = id,
        name = name,
        imagePath = imagePath,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pageCount = pageCount,
        fileSize = fileSize,
        pdfPath = pdfPath,
        pdfSize = pdfSize,
        enhancedImagePath = enhancedImagePath,
        ocrText = ocrText,
        ocrTimestamp = ocrTimestamp
    )

    companion object {
        fun fromDomain(document: Document) = DocumentEntity(
            id = document.id,
            name = document.name,
            imagePath = document.imagePath,
            createdAt = document.createdAt,
            updatedAt = document.updatedAt,
            pageCount = document.pageCount,
            fileSize = document.fileSize,
            pdfPath = document.pdfPath,
            pdfSize = document.pdfSize,
            enhancedImagePath = document.enhancedImagePath,
            ocrText = document.ocrText,
            ocrTimestamp = document.ocrTimestamp
        )
    }
}
