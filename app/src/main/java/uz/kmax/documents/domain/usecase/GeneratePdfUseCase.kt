package uz.kmax.documents.domain.usecase

import uz.kmax.documents.domain.model.Document
import uz.kmax.documents.domain.repository.DocumentRepository

class GeneratePdfUseCase(private val repository: DocumentRepository) {
    suspend operator fun invoke(document: Document): Document? {
        return repository.generatePdf(document)
    }
}
