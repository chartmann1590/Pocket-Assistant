package com.charles.pocketassistant.util

import com.charles.pocketassistant.domain.LatestRelease
import com.charles.pocketassistant.domain.UpdateDecision

object UpdateDecider {
    const val GRACE_MILLIS: Long = 5 * 60 * 1000L
    const val MIN_CHECK_INTERVAL_MILLIS: Long = 6 * 60 * 60 * 1000L

    fun shouldCheck(
        nowMillis: Long,
        lastCheckMillis: Long,
        minIntervalMillis: Long = MIN_CHECK_INTERVAL_MILLIS
    ): Boolean = (nowMillis - lastCheckMillis) >= minIntervalMillis

    fun decide(
        release: LatestRelease?,
        installLastUpdateMillis: Long,
        dismissedTag: String,
        graceMillis: Long = GRACE_MILLIS
    ): UpdateDecision {
        if (release == null) return UpdateDecision.CheckSkipped
        if (release.apkAssetUrl.isBlank()) return UpdateDecision.UpToDate
        if (release.tag.isNotBlank() && release.tag == dismissedTag) return UpdateDecision.Dismissed
        val newer = release.publishedAtMillis > (installLastUpdateMillis + graceMillis)
        return if (newer) UpdateDecision.Available(release) else UpdateDecision.UpToDate
    }
}
