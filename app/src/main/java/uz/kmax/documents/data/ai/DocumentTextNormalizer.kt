package uz.kmax.documents.data.ai

class DocumentTextNormalizer {

    fun normalize(text: String): String {
        if (text.isBlank()) return ""

        return text
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n") // Join with double line breaks for paragraph-like separation
            .replace(Regex(" {2,}"), " ") // Replace multiple spaces with a single space
    }
}
