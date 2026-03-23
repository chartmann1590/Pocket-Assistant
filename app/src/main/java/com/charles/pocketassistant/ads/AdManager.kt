package com.charles.pocketassistant.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.charles.pocketassistant.BuildConfig
import com.charles.pocketassistant.data.datastore.SettingsStore
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore
) {
    companion object {
        private const val TAG = "AdManager"
    }

    val adsEnabled: Boolean = BuildConfig.ADS_ENABLED

    private var initialized = false
    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false
    private var rewardedAd: RewardedAd? = null
    private var isLoadingRewarded = false

    private val _rewardedReady = MutableStateFlow(false)
    val rewardedReady: StateFlow<Boolean> = _rewardedReady

    fun initialize() {
        if (!adsEnabled || initialized) return
        initialized = true
        MobileAds.initialize(context) {
            Log.d(TAG, "Mobile Ads SDK initialized")
            loadInterstitial()
            loadRewarded()
        }
    }

    /** Whether ads should currently be suppressed (user redeemed ad-free time). */
    fun isAdFree(): Boolean {
        if (!adsEnabled) return true
        val adFreeUntil = runBlocking { settingsStore.settings.first().adFreeUntil }
        return System.currentTimeMillis() < adFreeUntil
    }

    // ── Interstitial ────────────────────────────────────────────────

    private fun loadInterstitial() {
        if (!adsEnabled || isLoadingInterstitial || interstitialAd != null) return
        isLoadingInterstitial = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial loaded")
                    interstitialAd = ad
                    isLoadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Interstitial failed to load: ${error.message}")
                    interstitialAd = null
                    isLoadingInterstitial = false
                }
            }
        )
    }

    fun showInterstitial(activity: Activity): Boolean {
        if (isAdFree()) return false
        val ad = interstitialAd ?: return false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                interstitialAd = null
                loadInterstitial()
            }
        }
        ad.show(activity)
        return true
    }

    // ── Rewarded ────────────────────────────────────────────────────

    fun loadRewarded() {
        if (!adsEnabled || isLoadingRewarded || rewardedAd != null) return
        isLoadingRewarded = true
        _rewardedReady.value = false
        RewardedAd.load(
            context,
            BuildConfig.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded")
                    rewardedAd = ad
                    isLoadingRewarded = false
                    _rewardedReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Rewarded ad failed to load: ${error.message}")
                    rewardedAd = null
                    isLoadingRewarded = false
                    _rewardedReady.value = false
                }
            }
        )
    }

    /**
     * Show a rewarded ad. Calls [onRewarded] when the user earns the reward.
     * Returns true if an ad was shown.
     */
    fun showRewarded(activity: Activity, onRewarded: () -> Unit): Boolean {
        val ad = rewardedAd ?: return false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                _rewardedReady.value = false
                loadRewarded()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                rewardedAd = null
                _rewardedReady.value = false
                loadRewarded()
            }
        }
        ad.show(activity) { _ ->
            Log.d(TAG, "User earned reward")
            onRewarded()
        }
        return true
    }
}
