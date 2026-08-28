package uz.kmax.documents.domain.model

/**
 * Represents the user's premium entitlement status.
 */
enum class PremiumEntitlement {
    /**
     * The entitlement status is not yet determined (e.g., billing client connecting).
     */
    UNKNOWN,

    /**
     * The user is on the free tier.
     */
    FREE,

    /**
     * The user has an active premium subscription.
     */
    PREMIUM
}
