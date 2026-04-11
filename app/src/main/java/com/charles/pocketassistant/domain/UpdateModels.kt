package com.charles.pocketassistant.domain

data class LatestRelease(
    val tag: String,
    val name: String,
    val htmlUrl: String,
    val publishedAtMillis: Long,
    val apkAssetUrl: String,
    val apkSizeBytes: Long,
    val notes: String
)

sealed interface UpdateDecision {
    data object UpToDate : UpdateDecision
    data object CheckSkipped : UpdateDecision
    data object Dismissed : UpdateDecision
    data class Available(val release: LatestRelease) : UpdateDecision
}
