package uz.kmax.documents.data.local

import android.content.Context
import uz.kmax.documents.domain.model.PremiumEntitlement

/**
 * A safe local cache for the last known entitlement state to improve UX.
 * Note: This is not a permanent proof of purchase; the source of truth is Google Play.
 */
class EntitlementLocalCache(context: Context) {
    private val prefs = context.getSharedPreferences("lumora_billing_prefs", Context.MODE_PRIVATE)

    var lastKnownEntitlement: PremiumEntitlement
        get() {
            val name = prefs.getString(KEY_ENTITLEMENT, PremiumEntitlement.FREE.name)
            return try {
                PremiumEntitlement.valueOf(name ?: PremiumEntitlement.FREE.name)
            } catch (e: Exception) {
                PremiumEntitlement.FREE
            }
        }
        set(value) {
            prefs.edit().putString(KEY_ENTITLEMENT, value.name).apply()
        }

    companion object {
        private const val KEY_ENTITLEMENT = "premium_entitlement"
    }
}
