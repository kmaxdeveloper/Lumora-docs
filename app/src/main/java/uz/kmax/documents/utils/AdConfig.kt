package uz.kmax.documents.utils

/**
 * Ad unit IDs and configuration for Google AdMob and Yandex Mobile Ads.
 * Standard sample/test IDs are used for development.
 */
object AdConfig {
    // Google AdMob Real Ad Units
    const val ADMOB_BANNER_ID = "ca-app-pub-4664801446868642/3396823052"
    const val ADMOB_INTERSTITIAL_ID = "ca-app-pub-4664801446868642/4823681291"

    // Yandex Mobile Ads Real Ad Units
    const val YANDEX_BANNER_ID = "R-M-19815410-1"
    const val YANDEX_INTERSTITIAL_ID = "R-M-19815410-3"

    // Interstitial frequency cap: minimum seconds between interstitial ads
    const val INTERSTITIAL_COOLDOWN_MS = 25_000L
}
