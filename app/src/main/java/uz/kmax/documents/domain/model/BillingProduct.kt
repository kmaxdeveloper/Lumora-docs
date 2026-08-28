package uz.kmax.documents.domain.model

/**
 * Domain model representing a product available for purchase.
 */
data class BillingProduct(
    val id: String,
    val name: String,
    val description: String,
    val formattedPrice: String,
    val period: String? = null
)
