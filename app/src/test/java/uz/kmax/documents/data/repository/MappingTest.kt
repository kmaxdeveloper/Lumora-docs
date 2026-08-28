package uz.kmax.documents.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import uz.kmax.documents.data.local.entity.DocumentEntity
import uz.kmax.documents.domain.model.Document

class MappingTest {

    @Test
    fun `DocumentEntity toDomain correctly maps all fields`() {
        // Arrange
        val entity = DocumentEntity(
            id = "1",
            name = "Test Doc",
            imagePath = "/path/to/img",
            createdAt = 1000L,
            updatedAt = 2000L,
            pageCount = 1,
            fileSize = 1024L,
            pdfPath = "/path/to/pdf",
            pdfSize = 512L,
            enhancedImagePath = "/path/to/enhanced",
            ocrText = "Extracted text",
            ocrTimestamp = 1500L
        )

        // Act
        val domain = entity.toDomain()

        // Assert
        assertEquals(entity.id, domain.id)
        assertEquals(entity.name, domain.name)
        assertEquals(entity.imagePath, domain.imagePath)
        assertEquals(entity.createdAt, domain.createdAt)
        assertEquals(entity.updatedAt, domain.updatedAt)
        assertEquals(entity.pageCount, domain.pageCount)
        assertEquals(entity.fileSize, domain.fileSize)
        assertEquals(entity.pdfPath, domain.pdfPath)
        assertEquals(entity.pdfSize, domain.pdfSize)
        assertEquals(entity.enhancedImagePath, domain.enhancedImagePath)
        assertEquals(entity.ocrText, domain.ocrText)
        assertEquals(entity.ocrTimestamp, domain.ocrTimestamp)
    }

    @Test
    fun `DocumentEntity fromDomain correctly maps all fields`() {
        // Arrange
        val domain = Document(
            id = "1",
            name = "Test Doc",
            imagePath = "/path/to/img",
            createdAt = 1000L,
            updatedAt = 2000L,
            pageCount = 1,
            fileSize = 1024L,
            pdfPath = "/path/to/pdf",
            pdfSize = 512L,
            enhancedImagePath = "/path/to/enhanced",
            ocrText = "Extracted text",
            ocrTimestamp = 1500L
        )

        // Act
        val entity = DocumentEntity.fromDomain(domain)

        // Assert
        assertEquals(domain.id, entity.id)
        assertEquals(domain.name, entity.name)
        assertEquals(domain.imagePath, entity.imagePath)
        assertEquals(domain.createdAt, entity.createdAt)
        assertEquals(domain.updatedAt, entity.updatedAt)
        assertEquals(domain.pageCount, entity.pageCount)
        assertEquals(domain.fileSize, entity.fileSize)
        assertEquals(domain.pdfPath, entity.pdfPath)
        assertEquals(domain.pdfSize, entity.pdfSize)
        assertEquals(domain.enhancedImagePath, entity.enhancedImagePath)
        assertEquals(domain.ocrText, entity.ocrText)
        assertEquals(domain.ocrTimestamp, entity.ocrTimestamp)
    }
}
