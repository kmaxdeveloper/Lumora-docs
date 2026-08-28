package uz.kmax.documents.domain.usecase.ai

import uz.kmax.documents.data.ai.DocumentContextBuilder
import uz.kmax.documents.domain.model.Document
import uz.kmax.documents.domain.model.ai.DocumentContext

class PrepareDocumentForAiUseCase(private val builder: DocumentContextBuilder) {
    suspend operator fun invoke(document: Document): DocumentContext? {
        return builder.build(document)
    }
}
