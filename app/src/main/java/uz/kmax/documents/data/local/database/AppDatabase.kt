package uz.kmax.documents.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import uz.kmax.documents.data.local.dao.DocumentDao
import uz.kmax.documents.data.local.dao.DocumentPageDao
import uz.kmax.documents.data.local.entity.DocumentAiSummaryEntity
import uz.kmax.documents.data.local.entity.DocumentEntity
import uz.kmax.documents.data.local.entity.DocumentPageEntity
import uz.kmax.documents.data.local.entity.DocumentPageOcrEntity

@Database(entities = [DocumentEntity::class, DocumentAiSummaryEntity::class, DocumentPageEntity::class, DocumentPageOcrEntity::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun documentPageDao(): DocumentPageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN pdfPath TEXT")
                db.execSQL("ALTER TABLE documents ADD COLUMN pdfSize INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN enhancedImagePath TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN ocrText TEXT")
                db.execSQL("ALTER TABLE documents ADD COLUMN ocrTimestamp INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `document_summaries` (
                        `documentId` TEXT NOT NULL, 
                        `summary` TEXT NOT NULL, 
                        `keyPoints` TEXT NOT NULL, 
                        `importantFacts` TEXT NOT NULL, 
                        `warnings` TEXT NOT NULL, 
                        `generatedAt` INTEGER NOT NULL, 
                        `sourceTextHash` INTEGER NOT NULL, 
                        `inputTokens` INTEGER NOT NULL, 
                        `outputTokens` INTEGER NOT NULL, 
                        PRIMARY KEY(`documentId`), 
                        FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_summaries_documentId` ON `document_summaries` (`documentId`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create document_pages table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `document_pages` (
                        `id` TEXT NOT NULL, 
                        `documentId` TEXT NOT NULL, 
                        `pageIndex` INTEGER NOT NULL, 
                        `imagePath` TEXT NOT NULL, 
                        `enhancedImagePath` TEXT, 
                        `createdAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_pages_documentId` ON `document_pages` (`documentId`)")

                // Migrate existing documents to pages
                db.execSQL("""
                    INSERT INTO document_pages (id, documentId, pageIndex, imagePath, enhancedImagePath, createdAt)
                    SELECT id || '_page0', id, 0, imagePath, enhancedImagePath, createdAt FROM documents
                """.trimIndent())
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `page_ocr` (
                        `pageId` TEXT NOT NULL, 
                        `text` TEXT NOT NULL, 
                        `processedAt` INTEGER NOT NULL, 
                        `confidence` REAL, 
                        PRIMARY KEY(`pageId`), 
                        FOREIGN KEY(`pageId`) REFERENCES `document_pages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_ocr_pageId` ON `page_ocr` (`pageId`)")

                // Migrate old OCR text to the first page
                db.execSQL("""
                    INSERT INTO page_ocr (pageId, text, processedAt, confidence)
                    SELECT id || '_page0', ocrText, ocrTimestamp, NULL FROM documents WHERE ocrText IS NOT NULL
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lumora_docs_db"
                )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
