package uz.kmax.documents.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import uz.kmax.documents.data.local.dao.DocumentDao
import uz.kmax.documents.data.local.dao.DocumentPageDao
import uz.kmax.documents.data.local.entity.DocumentEntity
import uz.kmax.documents.data.local.entity.DocumentPageEntity
import uz.kmax.documents.domain.model.Document
import uz.kmax.documents.domain.model.DocumentPage
import uz.kmax.documents.domain.repository.DocumentRepository
import uz.kmax.documents.data.pdf.PdfGenerator
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class DocumentRepositoryImpl(
    private val context: Context,
    private val dao: DocumentDao,
    private val pageDao: DocumentPageDao
) : DocumentRepository {

    private val pdfGenerator = PdfGenerator(context)

    private val documentsDir = File(context.filesDir, "documents").apply {
        if (!exists()) mkdirs()
    }

    private val pdfDir = File(context.filesDir, "pdf").apply {
        if (!exists()) mkdirs()
    }

    override fun observeDocuments(): Flow<List<Document>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeRecentDocuments(limit: Int): Flow<List<Document>> =
        dao.observeRecent(limit).map { entities -> entities.map { it.toDomain() } }

    override fun observeDocument(id: String): Flow<Document?> {
        val documentFlow = dao.observeById(id)
        val pagesFlow = pageDao.observePagesByDocumentId(id)
        val ocrFlow = pageDao.observeOcrByDocumentId(id)
        
        return combine(documentFlow, pagesFlow, ocrFlow) { docEntity, pageEntities, ocrEntities ->
            val ocrMap = ocrEntities.associateBy { it.pageId }
            docEntity?.toDomain()?.copy(
                pages = pageEntities.map { p -> 
                    p.toDomain().copy(
                        ocrText = ocrMap[p.id]?.text,
                        ocrTimestamp = ocrMap[p.id]?.processedAt
                    )
                }
            )
        }
    }

    override suspend fun getDocument(id: String): Document? = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext null
        val pageEntities = pageDao.getPagesByDocumentId(id)
        val ocrEntities = pageDao.getOcrByDocumentId(id).associateBy { it.pageId }
        
        val pages = pageEntities.map { p -> 
            p.toDomain().copy(
                ocrText = ocrEntities[p.id]?.text,
                ocrTimestamp = ocrEntities[p.id]?.processedAt
            )
        }
        entity.toDomain().copy(pages = pages)
    }

    override suspend fun saveDocument(bitmap: Bitmap, name: String?): Document? = withContext(Dispatchers.IO) {
        val documentId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val docDir = File(documentsDir, documentId)
        if (!docDir.mkdirs()) return@withContext null

        val pageId = UUID.randomUUID().toString()
        val fileName = "page_000.jpg"
        val file = File(docDir, fileName)

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val defaultName = "Scan " + SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
            val documentName = name ?: defaultName

            val docEntity = DocumentEntity(
                id = documentId,
                name = documentName,
                imagePath = file.absolutePath,
                createdAt = timestamp,
                updatedAt = timestamp,
                pageCount = 1,
                fileSize = file.length()
            )

            val pageEntity = DocumentPageEntity(
                id = pageId,
                documentId = documentId,
                pageIndex = 0,
                imagePath = file.absolutePath,
                enhancedImagePath = null,
                createdAt = timestamp
            )

            dao.insert(docEntity)
            pageDao.insert(pageEntity)
            
            docEntity.toDomain().copy(pages = listOf(pageEntity.toDomain()))
        } catch (e: Exception) {
            docDir.deleteRecursively()
            null
        }
    }

    override suspend fun saveMultiPageDocument(imagePaths: List<String>, name: String?): Document? = withContext(Dispatchers.IO) {
        val documentId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        
        val docDir = File(documentsDir, documentId)
        if (!docDir.mkdirs()) return@withContext null
        
        try {
            val pages = mutableListOf<DocumentPageEntity>()
            var totalSize = 0L
            
            imagePaths.forEachIndexed { index, path ->
                val pageId = UUID.randomUUID().toString()
                val fileName = "page_${index.toString().padStart(3, '0')}.jpg"
                val destFile = File(docDir, fileName)
                
                val sourceFile = File(path)
                if (sourceFile.exists()) {
                    sourceFile.copyTo(destFile, overwrite = true)
                    totalSize += destFile.length()
                    
                    pages.add(DocumentPageEntity(
                        id = pageId,
                        documentId = documentId,
                        pageIndex = index,
                        imagePath = destFile.absolutePath,
                        enhancedImagePath = null,
                        createdAt = timestamp
                    ))
                }
            }

            val defaultName = "Scan " + SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
            val documentName = name ?: defaultName

            val docEntity = DocumentEntity(
                id = documentId,
                name = documentName,
                imagePath = pages.firstOrNull()?.imagePath ?: "",
                createdAt = timestamp,
                updatedAt = timestamp,
                pageCount = pages.size,
                fileSize = totalSize
            )

            dao.insert(docEntity)
            pageDao.insertAll(pages)
            
            docEntity.toDomain().copy(pages = pages.map { it.toDomain() })
        } catch (e: Exception) {
            Log.e("DocumentRepo", "Multi-page save failed, cleaning up", e)
            docDir.deleteRecursively()
            null
        }
    }

    override suspend fun updateDocument(document: Document) {
        dao.update(DocumentEntity.fromDomain(document))
    }

    override suspend fun deleteDocument(document: Document) = withContext(Dispatchers.IO) {
        val docDir = File(documentsDir, document.id)
        if (docDir.exists() && docDir.isDirectory) {
            docDir.deleteRecursively()
        } else {
            val imageFile = File(document.imagePath)
            if (imageFile.exists()) imageFile.delete()
            
            document.enhancedImagePath?.let {
                val enhancedFile = File(it)
                if (enhancedFile.exists()) enhancedFile.delete()
            }
        }

        document.pdfPath?.let {
            val pdfFile = File(it)
            if (pdfFile.exists()) pdfFile.delete()
        }
        
        dao.delete(DocumentEntity.fromDomain(document))
    }

    override suspend fun generatePdf(document: Document): Document? = withContext(Dispatchers.IO) {
        val pdfFile = File(pdfDir, "${document.id}.pdf")
        
        if (pdfFile.exists()) pdfFile.delete()
        
        val pages = if (document.pages.isNotEmpty()) {
            document.pages.map { File(it.activeImagePath) }
        } else {
            listOf(File(document.activeImagePath))
        }

        val success = pdfGenerator.generateMultiple(pages, pdfFile)
        
        if (success) {
            val updated = document.copy(
                pdfPath = pdfFile.absolutePath,
                pdfSize = pdfFile.length(),
                updatedAt = System.currentTimeMillis()
            )
            dao.update(DocumentEntity.fromDomain(updated))
            updated
        } else {
            null
        }
    }

    override suspend fun deletePdf(document: Document): Document = withContext(Dispatchers.IO) {
        document.pdfPath?.let {
            val file = File(it)
            if (file.exists()) file.delete()
        }
        
        val updated = document.copy(
            pdfPath = null,
            pdfSize = 0L,
            updatedAt = System.currentTimeMillis()
        )
        dao.update(DocumentEntity.fromDomain(updated))
        updated
    }

    override suspend fun duplicateDocument(document: Document): Document? = withContext(Dispatchers.IO) {
        val newDocumentId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val newDocDir = File(documentsDir, newDocumentId).apply { mkdirs() }
        
        try {
            val newPages = mutableListOf<DocumentPageEntity>()
            val oldPageEntities = pageDao.getPagesByDocumentId(document.id)
            
            oldPageEntities.forEachIndexed { index, oldPage ->
                val newPageId = UUID.randomUUID().toString()
                
                val oldImageFile = File(oldPage.imagePath)
                val newImageFileName = "page_${index.toString().padStart(3, '0')}.jpg"
                val newImageFile = File(newDocDir, newImageFileName)
                if (oldImageFile.exists()) {
                    oldImageFile.copyTo(newImageFile, overwrite = true)
                }
                
                var newEnhancedPath: String? = null
                oldPage.enhancedImagePath?.let { oldPath ->
                    val oldEnhancedFile = File(oldPath)
                    if (oldEnhancedFile.exists()) {
                        val newEnhancedFileName = "page_${index.toString().padStart(3, '0')}_enhanced.jpg"
                        val newEnhancedFile = File(newDocDir, newEnhancedFileName)
                        oldEnhancedFile.copyTo(newEnhancedFile, overwrite = true)
                        newEnhancedPath = newEnhancedFile.absolutePath
                    }
                }
                
                newPages.add(DocumentPageEntity(
                    id = newPageId,
                    documentId = newDocumentId,
                    pageIndex = index,
                    imagePath = newImageFile.absolutePath,
                    enhancedImagePath = newEnhancedPath,
                    createdAt = timestamp
                ))
            }
            
            var newDocName = "${document.name} copy"
            var counter = 2
            while (dao.getByName(newDocName) != null) {
                newDocName = "${document.name} copy $counter"
                counter++
            }

            val newDocEntity = DocumentEntity(
                id = newDocumentId,
                name = newDocName,
                imagePath = newPages.firstOrNull()?.imagePath ?: "",
                createdAt = timestamp,
                updatedAt = timestamp,
                pageCount = newPages.size,
                fileSize = document.fileSize,
                pdfPath = null,
                pdfSize = 0L,
                enhancedImagePath = newPages.firstOrNull()?.enhancedImagePath,
                ocrText = null,
                ocrTimestamp = null
            )
            
            dao.insert(newDocEntity)
            pageDao.insertAll(newPages)
            
            newDocEntity.toDomain().copy(pages = newPages.map { it.toDomain() })
        } catch (e: Exception) {
            newDocDir.deleteRecursively()
            null
        }
    }

    override suspend fun getPages(documentId: String): List<DocumentPage> = withContext(Dispatchers.IO) {
        val pageEntities = pageDao.getPagesByDocumentId(documentId)
        val ocrEntities = pageDao.getOcrByDocumentId(documentId).associateBy { it.pageId }
        
        pageEntities.map { p -> 
            p.toDomain().copy(
                ocrText = ocrEntities[p.id]?.text,
                ocrTimestamp = ocrEntities[p.id]?.processedAt
            )
        }
    }

    override fun observePages(documentId: String): Flow<List<DocumentPage>> {
        val pagesFlow = pageDao.observePagesByDocumentId(documentId)
        val ocrFlow = pageDao.observeOcrByDocumentId(documentId)
        
        return combine(pagesFlow, ocrFlow) { pageEntities, ocrEntities ->
            val ocrMap = ocrEntities.associateBy { it.pageId }
            pageEntities.map { p -> 
                p.toDomain().copy(
                    ocrText = ocrMap[p.id]?.text,
                    ocrTimestamp = ocrMap[p.id]?.processedAt
                )
            }
        }
    }

    override suspend fun updatePageOrder(pages: List<DocumentPage>) = withContext(Dispatchers.IO) {
        pageDao.insertAll(pages.map { DocumentPageEntity.fromDomain(it) })
        
        // Mark PDF as stale/delete it
        if (pages.isNotEmpty()) {
            dao.getById(pages[0].documentId)?.let { entity ->
                val doc = entity.toDomain()
                if (doc.pdfPath != null) {
                    deletePdf(doc)
                }
            }
        }
    }

    override suspend fun deletePage(page: DocumentPage): Unit = withContext(Dispatchers.IO) {
        val file = File(page.imagePath)
        if (file.exists()) file.delete()
        page.enhancedImagePath?.let { File(it).apply { if (exists()) delete() } }
        
        pageDao.delete(DocumentPageEntity.fromDomain(page))
        
        dao.getById(page.documentId)?.let { doc ->
            val updatedDoc = doc.copy(
                pageCount = doc.pageCount - 1, 
                updatedAt = System.currentTimeMillis(),
                pdfPath = null, // Invalidate PDF
                pdfSize = 0L
            )
            dao.update(updatedDoc)
        }
        Unit
    }

    override suspend fun saveEnhancedImage(document: Document, bitmap: Bitmap): Document? = withContext(Dispatchers.IO) {
        val fileName = "${document.id}_enhanced.jpg"
        val file = File(documentsDir, fileName)

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val updated = document.copy(
                enhancedImagePath = file.absolutePath,
                updatedAt = System.currentTimeMillis(),
                pdfPath = null, // Invalidate PDF
                pdfSize = 0L
            )

            dao.update(DocumentEntity.fromDomain(updated))
            
            // Also update the first page if exists
            val pages = pageDao.getPagesByDocumentId(document.id)
            if (pages.isNotEmpty()) {
                val firstPage = pages.first()
                pageDao.update(firstPage.copy(enhancedImagePath = file.absolutePath))
            }

            updated
        } catch (e: Exception) {
            if (file.exists()) file.delete()
            null
        }
    }

    override suspend fun saveEnhancedPageImage(page: DocumentPage, bitmap: Bitmap): DocumentPage? = withContext(Dispatchers.IO) {
        val docDir = File(documentsDir, page.documentId).apply { if (!exists()) mkdirs() }
        val fileName = "page_${page.pageIndex.toString().padStart(3, '0')}_enhanced.jpg"
        val file = File(docDir, fileName)

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val updatedPage = page.copy(enhancedImagePath = file.absolutePath)
            pageDao.update(DocumentPageEntity.fromDomain(updatedPage))

            // Invalidate PDF and update doc if this is the first page
            dao.getById(page.documentId)?.let { docEntity ->
                val updatedDoc = docEntity.copy(
                    updatedAt = System.currentTimeMillis(),
                    pdfPath = null,
                    pdfSize = 0L,
                    enhancedImagePath = if (page.pageIndex == 0) file.absolutePath else docEntity.enhancedImagePath
                )
                dao.update(updatedDoc)
            }

            updatedPage
        } catch (e: Exception) {
            if (file.exists()) file.delete()
            null
        }
    }
}
