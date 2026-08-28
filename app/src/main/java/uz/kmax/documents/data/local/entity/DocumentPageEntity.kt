package uz.kmax.documents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import uz.kmax.documents.domain.model.DocumentPage

@Entity(
    tableName = "document_pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId"])]
)
data class DocumentPageEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageIndex: Int,
    val imagePath: String,
    val enhancedImagePath: String?,
    val createdAt: Long
) {
    fun toDomain() = DocumentPage(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        imagePath = imagePath,
        enhancedImagePath = enhancedImagePath,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(page: DocumentPage) = DocumentPageEntity(
            id = page.id,
            documentId = page.documentId,
            pageIndex = page.pageIndex,
            imagePath = page.imagePath,
            enhancedImagePath = page.enhancedImagePath,
            createdAt = page.createdAt
        )
    }
}
