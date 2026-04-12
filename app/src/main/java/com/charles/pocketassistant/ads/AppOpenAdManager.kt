package com.charles.pocketassistant.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.charles.pocketassistant.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppOpenAdManager @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val adManager: AdManager
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    companion object {
        private const val TAG = "AppOpenAdManager"
        private const val EXPIRY_MILLIS = 4 * 60 * 60 * 1000L // 4 hours
    }

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var loadTimeMillis = 0L
    private var currentActivity: Activity? = null

    fun attach(application: Application) {
        if (!adManager.adsEnabled) return
        if (BuildConfig.ADMOB_OPEN_AD_ID.isBlank()) return
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    // ── DefaultLifecycleObserver ──────────────────────────────────────

    override fun onStart(owner: LifecycleOwner) {
        showAdIfAvailable()
    }

    // ── ActivityLifecycleCallbacks ────────────────────────────────────

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) { currentActivity = activity }
    override fun onActivityResumed(activity: Activity) { currentActivity = activity }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    // ── Load / Show ──────────────────────────────────────────────────

    fun loadAd() {
        if (!adManager.adsEnabled) return
        if (isLoadingAd || isAdAvailable()) return
        val adUnitId = BuildConfig.ADMOB_OPEN_AD_ID
        if (adUnitId.isBlank()) return

        isLoadingAd = true
        AppOpenAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "App open ad loaded")
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTimeMillis = System.currentTimeMillis()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "App open ad failed to load: ${error.message}")
                    isLoadingAd = false
                }
            }
        )
    }

    private fun isAdAvailable(): Boolean {
        val ad = appOpenAd ?: return false
        val elapsed = System.currentTimeMillis() - loadTimeMillis
        return elapsed < EXPIRY_MILLIS
    }

    private fun showAdIfAvailable() {
        if (isShowingAd) return
        if (adManager.isAdFree()) return
        if (!isAdAvailable()) {
            loadAd()
            return
        }
        val activity = currentActivity ?: return
        val ad = appOpenAd ?: return

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "App open ad dismissed")
                appOpenAd = null
                isShowingAd = false
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "App open ad failed to show: ${error.message}")
                appOpenAd = null
                isShowingAd = false
                loadAd()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App open ad showing")
            }
        }

        isShowingAd = true
        ad.show(activity)
    }
}
