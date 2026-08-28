package uz.kmax.documents.domain.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest as AdMobRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd as AdMobInterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.security.ProviderInstaller
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest as YandexAdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.YandexAds
import com.yandex.mobile.ads.interstitial.InterstitialAd as YandexInterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import kotlinx.coroutines.launch
import uz.kmax.documents.domain.model.PremiumEntitlement
import uz.kmax.documents.domain.repository.BillingRepository
import uz.kmax.documents.utils.AdConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Robust Multi-Network Ad Manager orchestrating Google AdMob and Yandex Mobile Ads
 * in a seamless Waterfall/Failover strategy.
 *
 * Includes memory leak prevention, lifecycle awareness, activity safety checks,
 * ad expiration handling, and single-execution callback guarantees.
 */
object AdsManager {

    private const val TAG = "AdsManager"
    private const val AD_EXPIRATION_MS = 50 * 60 * 1000L // 50 minutes expiration for preloaded ads

    private var isInitialized = false
    private var lastInterstitialShownTime = 0L

    // Cached interstitial instances and timestamps
    private var cachedAdMobInterstitial: AdMobInterstitialAd? = null
    private var cachedAdMobTime = 0L

    private var cachedYandexInterstitial: YandexInterstitialAd? = null
    private var cachedYandexTime = 0L

    private var isAdMobInterstitialLoading = false
    private var isYandexInterstitialLoading = false

    /**
     * Initializes both Google AdMob and Yandex Mobile Ads SDKs safely.
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true

        try {
            // Fix for TLSv1 not supported crash on newer Android versions
            ProviderInstaller.installIfNeeded(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing security provider", e)
        }

        try {
            // 1. Google Mobile Ads
            com.google.android.gms.ads.MobileAds.initialize(context.applicationContext) { status ->
                Log.d(TAG, "AdMob initialized successfully: $status")
            }

            // 2. Yandex Mobile Ads
            YandexAds.initialize(context.applicationContext) {
                Log.d(TAG, "Yandex Mobile Ads initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Ad SDKs", e)
        }
    }

    /**
     * Checks whether No-Ads is active (via subscription, purchase, or debug override).
     */
    fun isNoAdsActive(billingRepository: BillingRepository): Boolean {
        return billingRepository.entitlement.value == PremiumEntitlement.PREMIUM
    }

    // ─────────────────────────────────────────────────────────────────────
    //  BANNER ADS (Waterfall: AdMob -> Yandex)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Loads a banner ad into [container] with lifecycle cleanup and memory protection.
     */
    fun loadBanner(
        container: ViewGroup,
        billingRepository: BillingRepository,
        lifecycleOwner: LifecycleOwner,
    ) {
        // Clean up previous banner inside container before attaching new observer
        cleanupContainer(container)

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                billingRepository.entitlement.collect { entitlement ->
                    if (entitlement == PremiumEntitlement.PREMIUM) {
                        cleanupContainer(container)
                    } else {
                        loadBannerWaterfall(container, lifecycleOwner)
                    }
                }
            }
        }

        // Attach lifecycle observer to destroy AdView when Fragment is destroyed
        lifecycleOwner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    cleanupContainer(container)
                    owner.lifecycle.removeObserver(this)
                }
            }
        )
    }

    private fun cleanupContainer(container: ViewGroup) {
        try {
            for (i in 0 until container.childCount) {
                when (val child = container.getChildAt(i)) {
                    is AdView -> child.destroy()
                    is BannerAdView -> child.destroy()
                }
            }
            container.removeAllViews()
            container.visibility = View.GONE
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up banner container", e)
        }
    }

    private fun loadBannerWaterfall(container: ViewGroup, lifecycleOwner: LifecycleOwner) {
        cleanupContainer(container)
        val context = container.context

        // Step 1: Try Google AdMob Banner
        val adMobView = AdView(context).apply {
            adUnitId = AdConfig.ADMOB_BANNER_ID
            setAdSize(AdSize.BANNER)
        }

        adMobView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    Log.d(TAG, "AdMob Banner loaded successfully")
                    cleanupContainer(container)
                    container.addView(adMobView)
                    container.visibility = View.VISIBLE
                } else {
                    adMobView.destroy()
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w(TAG, "AdMob Banner failed ($error), falling back to Yandex...")
                adMobView.destroy()
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    loadYandexBanner(container, lifecycleOwner)
                }
            }
        }

        val adRequest = AdMobRequest.Builder().build()
        adMobView.loadAd(adRequest)
    }

    private fun loadYandexBanner(container: ViewGroup, lifecycleOwner: LifecycleOwner) {
        val context = container.context
        val metrics = context.resources.displayMetrics
        val widthDp = (metrics.widthPixels / metrics.density).toInt().coerceAtLeast(320)

        val yandexBanner = BannerAdView(context).apply {
            setAdSize(BannerAdSize.sticky(context, widthDp))
        }

        yandexBanner.setBannerAdEventListener(object : BannerAdEventListener {
            override fun onAdLoaded() {
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    Log.d(TAG, "Yandex Banner loaded successfully")
                    cleanupContainer(container)
                    container.addView(yandexBanner)
                    container.visibility = View.VISIBLE
                } else {
                    yandexBanner.destroy()
                }
            }

            override fun onAdFailedToLoad(error: AdRequestError) {
                Log.w(TAG, "Yandex Banner failed ($error). No ads available.")
                yandexBanner.destroy()
                cleanupContainer(container)
            }

            override fun onAdClicked() {}
            override fun onImpression(impressionData: ImpressionData?) {}
        })

        val request = YandexAdRequest.Builder(AdConfig.YANDEX_BANNER_ID).build()
        yandexBanner.loadAd(request)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  INTERSTITIAL ADS (Waterfall: AdMob -> Yandex)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Pre-loads an interstitial ad in the background for instant display later.
     */
    fun preloadInterstitial(context: Context, billingRepository: BillingRepository) {
        if (isNoAdsActive(billingRepository)) return
        preloadAdMobInterstitial(context.applicationContext)
    }

    private fun preloadAdMobInterstitial(context: Context) {
        // Discard expired cached ad
        if (cachedAdMobInterstitial != null && ((System.currentTimeMillis() - cachedAdMobTime) > AD_EXPIRATION_MS)) {
            cachedAdMobInterstitial = null
        }

        if (cachedAdMobInterstitial != null || isAdMobInterstitialLoading) return
        isAdMobInterstitialLoading = true

        val request = AdMobRequest.Builder().build()
        AdMobInterstitialAd.load(
            context,
            AdConfig.ADMOB_INTERSTITIAL_ID,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: AdMobInterstitialAd) {
                    cachedAdMobInterstitial = ad
                    cachedAdMobTime = System.currentTimeMillis()
                    isAdMobInterstitialLoading = false
                    Log.d(TAG, "AdMob Interstitial preloaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    cachedAdMobInterstitial = null
                    isAdMobInterstitialLoading = false
                    Log.w(TAG, "AdMob Interstitial preload failed ($error). Trying Yandex...")
                    preloadYandexInterstitial(context)
                }
            }
        )
    }

    private fun preloadYandexInterstitial(context: Context) {
        // Discard expired cached ad
        if (cachedYandexInterstitial != null && (System.currentTimeMillis() - cachedYandexTime > AD_EXPIRATION_MS)) {
            cachedYandexInterstitial = null
        }

        if (cachedYandexInterstitial != null || isYandexInterstitialLoading) return
        isYandexInterstitialLoading = true

        val loader = InterstitialAdLoader(context)
        val request = YandexAdRequest.Builder(AdConfig.YANDEX_INTERSTITIAL_ID).build()
        loader.loadAd(request, object : InterstitialAdLoadListener {
            override fun onAdLoaded(interstitialAd: YandexInterstitialAd) {
                cachedYandexInterstitial = interstitialAd
                cachedYandexTime = System.currentTimeMillis()
                isYandexInterstitialLoading = false
                Log.d(TAG, "Yandex Interstitial preloaded successfully")
            }

            override fun onAdFailedToLoad(error: AdRequestError) {
                cachedYandexInterstitial = null
                isYandexInterstitialLoading = false
                Log.w(TAG, "Yandex Interstitial preload failed ($error).")
            }
        })
    }

    /**
     * Shows an interstitial ad using waterfall strategy (AdMob -> Yandex).
     * Guarantees [onDismiss] is called exactly ONCE under all conditions.
     */
    fun showInterstitial(
        activity: Activity,
        billingRepository: BillingRepository,
        onDismiss: () -> Unit
    ) {
        val dismissedFlag = AtomicBoolean(false)
        fun safeDismiss() {
            if (dismissedFlag.compareAndSet(false, true)) {
                activity.runOnUiThread { onDismiss() }
            }
        }

        // 1. Check No-Ads status
        if (isNoAdsActive(billingRepository)) {
            safeDismiss()
            return
        }

        // 2. Check Activity state
        if (activity.isFinishing || activity.isDestroyed) {
            safeDismiss()
            return
        }

        // 3. Frequency capping check
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownTime < AdConfig.INTERSTITIAL_COOLDOWN_MS) {
            safeDismiss()
            return
        }

        // 4. Check cached AdMob Interstitial (if not expired)
        if (cachedAdMobInterstitial != null && (now - cachedAdMobTime <= AD_EXPIRATION_MS)) {
            val adMobAd = cachedAdMobInterstitial
            cachedAdMobInterstitial = null
            
            adMobAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    lastInterstitialShownTime = System.currentTimeMillis()
                    preloadInterstitial(activity, billingRepository)
                    safeDismiss()
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    lastInterstitialShownTime = System.currentTimeMillis()
                    preloadInterstitial(activity, billingRepository)
                    safeDismiss()
                }
            }
            
            try {
                adMobAd?.show(activity)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing AdMob Interstitial", e)
                safeDismiss()
            }
            return
        }

        // 5. Check cached Yandex Interstitial (if not expired)
        if (cachedYandexInterstitial != null && (now - cachedYandexTime <= AD_EXPIRATION_MS)) {
            val yandexAd = cachedYandexInterstitial
            cachedYandexInterstitial = null

            yandexAd?.setAdEventListener(object : InterstitialAdEventListener {
                override fun onAdDismissed() {
                    lastInterstitialShownTime = System.currentTimeMillis()
                    preloadInterstitial(activity, billingRepository)
                    safeDismiss()
                }

                override fun onAdFailedToShow(adError: AdError) {
                    lastInterstitialShownTime = System.currentTimeMillis()
                    preloadInterstitial(activity, billingRepository)
                    safeDismiss()
                }

                override fun onAdShown() {}
                override fun onAdClicked() {}
                override fun onAdImpression(impressionData: ImpressionData?) {}
            })

            try {
                yandexAd?.show(activity)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing Yandex Interstitial", e)
                safeDismiss()
            }
            return
        }

        // 6. On-Demand Waterfall load & show
        loadAndShowOnDemand(activity, ::safeDismiss)
    }

    private fun loadAndShowOnDemand(
        activity: Activity,
        safeDismiss: () -> Unit
    ) {
        val request = AdMobRequest.Builder().build()
        AdMobInterstitialAd.load(
            activity,
            AdConfig.ADMOB_INTERSTITIAL_ID,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: AdMobInterstitialAd) {
                    if (activity.isFinishing || activity.isDestroyed) {
                        safeDismiss()
                        return
                    }

                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            lastInterstitialShownTime = System.currentTimeMillis()
                            safeDismiss()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                            safeDismiss()
                        }
                    }

                    try {
                        ad.show(activity)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing AdMob OnDemand Interstitial", e)
                        safeDismiss()
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "AdMob OnDemand Interstitial failed ($error), fallback to Yandex...")
                    loadAndShowYandexOnDemand(activity, safeDismiss)
                }
            }
        )
    }

    private fun loadAndShowYandexOnDemand(activity: Activity, safeDismiss: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) {
            safeDismiss()
            return
        }

        val loader = InterstitialAdLoader(activity.applicationContext)
        val request = YandexAdRequest.Builder(AdConfig.YANDEX_INTERSTITIAL_ID).build()
        loader.loadAd(request, object : InterstitialAdLoadListener {
            override fun onAdLoaded(interstitialAd: YandexInterstitialAd) {
                if (activity.isFinishing || activity.isDestroyed) {
                    safeDismiss()
                    return
                }

                interstitialAd.setAdEventListener(object : InterstitialAdEventListener {
                    override fun onAdDismissed() {
                        lastInterstitialShownTime = System.currentTimeMillis()
                        safeDismiss()
                    }

                    override fun onAdFailedToShow(adError: AdError) {
                        safeDismiss()
                    }

                    override fun onAdShown() {}
                    override fun onAdClicked() {}
                    override fun onAdImpression(impressionData: ImpressionData?) {}
                })

                try {
                    interstitialAd.show(activity)
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing Yandex OnDemand Interstitial", e)
                    safeDismiss()
                }
            }

            override fun onAdFailedToLoad(error: AdRequestError) {
                Log.w(TAG, "Yandex OnDemand Interstitial failed ($error).")
                safeDismiss()
            }
        })
    }
}
