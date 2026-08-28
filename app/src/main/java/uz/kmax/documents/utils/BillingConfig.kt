package uz.kmax.documents.utils

/**
 * Centralized configuration for Google Play Billing products.
 */
object BillingConfig {
    // These are placeholder IDs for development.
    // In production, these must match the IDs configured in the Google Play Console.
    const val PRODUCT_PREMIUM_MONTHLY = "lumora_premium_monthly"
    const val PRODUCT_PREMIUM_YEARLY = "lumora_premium_yearly"
    const val PRODUCT_NO_ADS = "lumora_no_ads_lifetime"

    val SUBSCRIPTION_PRODUCTS = listOf(
        PRODUCT_PREMIUM_MONTHLY,
        PRODUCT_PREMIUM_YEARLY
    )

    val IN_APP_PRODUCTS = listOf(
        PRODUCT_NO_ADS
    )

    val ALL_PRODUCTS = SUBSCRIPTION_PRODUCTS + IN_APP_PRODUCTS
}
