package uz.kmax.documents.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.kmax.documents.data.local.EntitlementLocalCache
import uz.kmax.documents.domain.model.BillingProduct
import uz.kmax.documents.domain.model.PremiumEntitlement
import uz.kmax.documents.domain.repository.BillingRepository
import uz.kmax.documents.utils.BillingConfig

/**
 * Implementation of [BillingRepository] using Google Play Billing Library.
 * Supports Subscriptions (Monthly, Yearly) and In-App products (No-Ads lifetime).
 */
class BillingRepositoryImpl(
    private val context: Context,
    private val localCache: EntitlementLocalCache
) : BillingRepository, PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var debugOverride: PremiumEntitlement? = null
    
    private val _entitlement = MutableStateFlow(localCache.lastKnownEntitlement)
    override val entitlement: StateFlow<PremiumEntitlement> = _entitlement.asStateFlow()

    private val _products = MutableStateFlow<List<BillingProduct>>(emptyList())
    override val products: StateFlow<List<BillingProduct>> = _products.asStateFlow()

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        queryProducts()
                        refreshEntitlement()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Will retry on next explicit call
            }
        })
    }

    private suspend fun queryProducts() {
        val allMapped = mutableListOf<BillingProduct>()

        // 1. Query Subscriptions
        val subProductList = BillingConfig.SUBSCRIPTION_PRODUCTS.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val subParams = QueryProductDetailsParams.newBuilder()
            .setProductList(subProductList)
            .build()

        val (subResult, subDetailsList) = billingClient.queryProductDetails(subParams)
        if (subResult.responseCode == BillingClient.BillingResponseCode.OK && subDetailsList != null) {
            allMapped += subDetailsList.map { details ->
                BillingProduct(
                    id = details.productId,
                    name = details.name,
                    description = details.description,
                    formattedPrice = details.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "N/A",
                    period = details.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.billingPeriod
                )
            }
        }

        // 2. Query In-App Products (e.g. No-Ads Lifetime)
        val inAppProductList = BillingConfig.IN_APP_PRODUCTS.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(inAppProductList)
            .build()

        val (inAppResult, inAppDetailsList) = billingClient.queryProductDetails(inAppParams)
        if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK && inAppDetailsList != null) {
            allMapped += inAppDetailsList.map { details ->
                BillingProduct(
                    id = details.productId,
                    name = details.name,
                    description = details.description,
                    formattedPrice = details.oneTimePurchaseOfferDetails?.formattedPrice ?: "N/A",
                    period = null
                )
            }
        }

        _products.value = allMapped
    }

    private suspend fun refreshEntitlement() {
        if (debugOverride != null) {
            _entitlement.value = debugOverride!!
            return
        }

        val allPurchases = mutableListOf<Purchase>()

        // 1. Query SUBS purchases
        val subParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val (subResult, subPurchases) = billingClient.queryPurchasesAsync(subParams)
        if (subResult.responseCode == BillingClient.BillingResponseCode.OK && subPurchases != null) {
            allPurchases += subPurchases
        }

        // 2. Query INAPP purchases (e.g. No-Ads)
        val inAppParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val (inAppResult, inAppPurchases) = billingClient.queryPurchasesAsync(inAppParams)
        if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK && inAppPurchases != null) {
            allPurchases += inAppPurchases
        }

        processPurchases(allPurchases)
    }

    private fun processPurchases(purchases: List<Purchase>?) {
        if (debugOverride != null) {
            _entitlement.value = debugOverride!!
            return
        }

        val isPremium = purchases?.any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED && 
            purchase.products.any { it in BillingConfig.ALL_PRODUCTS }
        } ?: false

        val newEntitlement = if (isPremium) PremiumEntitlement.PREMIUM else PremiumEntitlement.FREE
        _entitlement.value = newEntitlement
        localCache.lastKnownEntitlement = newEntitlement

        // Acknowledge purchases
        purchases?.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                scope.launch {
                    val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(acknowledgeParams)
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases)
        }
    }

    override suspend fun refresh() {
        if (!billingClient.isReady) {
            startConnection()
        } else {
            queryProducts()
            refreshEntitlement()
        }
    }

    override suspend fun launchPurchaseFlow(activity: Activity, productId: String): Result<Unit> {
        val isSubscription = productId in BillingConfig.SUBSCRIPTION_PRODUCTS
        val productType = if (isSubscription) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP

        val productDetails = getProductDetails(productId, productType) 
            ?: return Result.failure(Exception("Product details not found for $productId"))

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (isSubscription) {
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
                ?: return Result.failure(Exception("No subscription offer available"))
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .build()

        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        return if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Billing error: ${billingResult.debugMessage}"))
        }
    }

    private suspend fun getProductDetails(productId: String, productType: String): ProductDetails? {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val (_, productDetailsList) = billingClient.queryProductDetails(params)
        return productDetailsList?.firstOrNull { it.productId == productId }
    }

    override suspend fun restorePurchases(): Result<Unit> {
        refreshEntitlement()
        return Result.success(Unit)
    }

    override fun setDebugEntitlementOverride(override: PremiumEntitlement?) {
        debugOverride = override
        if (override != null) {
            _entitlement.value = override
        } else {
            scope.launch { refreshEntitlement() }
        }
    }
}
