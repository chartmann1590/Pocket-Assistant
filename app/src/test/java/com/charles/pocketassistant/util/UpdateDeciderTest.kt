package com.charles.pocketassistant.util

import com.charles.pocketassistant.domain.LatestRelease
import com.charles.pocketassistant.domain.UpdateDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDeciderTest {

    private fun release(
        tag: String = "ci-123",
        publishedAt: Long = 2_000L,
        apkAsset: String = "https://example.test/app-debug.apk"
    ): LatestRelease = LatestRelease(
        tag = tag,
        name = tag,
        htmlUrl = "https://example.test/release",
        publishedAtMillis = publishedAt,
        apkAssetUrl = apkAsset,
        apkSizeBytes = 1024L,
        notes = ""
    )

    @Test
    fun nullRelease_returnsCheckSkipped() {
        val decision = UpdateDecider.decide(
            release = null,
            installLastUpdateMillis = 0L,
            dismissedTag = ""
        )
        assertEquals(UpdateDecision.CheckSkipped, decision)
    }

    @Test
    fun blankApkAssetUrl_returnsUpToDate() {
        val decision = UpdateDecider.decide(
            release = release(apkAsset = ""),
            installLastUpdateMillis = 0L,
            dismissedTag = ""
        )
        assertEquals(UpdateDecision.UpToDate, decision)
    }

    @Test
    fun dismissedTag_returnsDismissed() {
        val decision = UpdateDecider.decide(
            release = release(tag = "ci-999", publishedAt = 10_000L),
            installLastUpdateMillis = 0L,
            dismissedTag = "ci-999"
        )
        assertEquals(UpdateDecision.Dismissed, decision)
    }

    @Test
    fun releaseNewerThanInstall_beyondGrace_returnsAvailable() {
        val install = 1_000L
        val published = install + UpdateDecider.GRACE_MILLIS + 1L
        val decision = UpdateDecider.decide(
            release = release(publishedAt = published),
            installLastUpdateMillis = install,
            dismissedTag = ""
        )
        assertTrue(decision is UpdateDecision.Available)
    }

    @Test
    fun releaseWithinGrace_returnsUpToDate() {
        val install = 10_000L
        val published = install + UpdateDecider.GRACE_MILLIS - 1L
        val decision = UpdateDecider.decide(
            release = release(publishedAt = published),
            installLastUpdateMillis = install,
            dismissedTag = ""
        )
        assertEquals(UpdateDecision.UpToDate, decision)
    }

    @Test
    fun releaseOlderThanInstall_returnsUpToDate() {
        val decision = UpdateDecider.decide(
            release = release(publishedAt = 1_000L),
            installLastUpdateMillis = 10_000L,
            dismissedTag = ""
        )
        assertEquals(UpdateDecision.UpToDate, decision)
    }

    @Test
    fun shouldCheck_trueWhenIntervalExceeded() {
        val now = 100_000L
        val last = now - UpdateDecider.MIN_CHECK_INTERVAL_MILLIS - 1L
        assertTrue(UpdateDecider.shouldCheck(now, last))
    }

    @Test
    fun shouldCheck_falseWhenWithinInterval() {
        val now = 100_000L
        val last = now - 1_000L
        assertFalse(UpdateDecider.shouldCheck(now, last))
    }

    @Test
    fun shouldCheck_trueWhenNeverCheckedAndNowBeyondInterval() {
        assertTrue(
            UpdateDecider.shouldCheck(
                nowMillis = UpdateDecider.MIN_CHECK_INTERVAL_MILLIS,
                lastCheckMillis = 0L
            )
        )
    }
}
