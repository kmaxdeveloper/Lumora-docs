package uz.kmax.documents.domain.repository

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow
import uz.kmax.documents.domain.model.BillingProduct
import uz.kmax.documents.domain.model.PremiumEntitlement

/**
 * Interface for managing monetization, Google Play Billing, and No-Ads entitlements.
 */
interface BillingRepository {
    /**
     * Observable flow of the current premium / no-ads entitlement status.
     */
    val entitlement: StateFlow<PremiumEntitlement>

    /**
     * Observable flow of available premium products.
     */
    val products: StateFlow<List<BillingProduct>>

    /**
     * Fetches the latest products and entitlement state from Google Play.
     */
    suspend fun refresh()

    /**
     * Initiates the purchase flow for the specified product (Subscription or One-time No-Ads).
     */
    suspend fun launchPurchaseFlow(activity: Activity, productId: String): Result<Unit>

    /**
     * Explicitly triggers a restore of previous purchases.
     */
    suspend fun restorePurchases(): Result<Unit>

    /**
     * Testing / Debug toggle to simulate No-Ads / Premium mode on device without purchase.
     */
    fun setDebugEntitlementOverride(override: PremiumEntitlement?)
}
