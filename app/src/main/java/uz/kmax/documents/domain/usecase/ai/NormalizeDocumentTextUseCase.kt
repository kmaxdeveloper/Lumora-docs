package uz.kmax.documents.domain.usecase.ai

import uz.kmax.documents.data.ai.DocumentTextNormalizer

class NormalizeDocumentTextUseCase(private val normalizer: DocumentTextNormalizer) {
    operator fun invoke(text: String): String {
        return normalizer.normalize(text)
    }
}
