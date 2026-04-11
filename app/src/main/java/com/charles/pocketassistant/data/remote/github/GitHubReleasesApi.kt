package com.charles.pocketassistant.data.remote.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

@Serializable
data class GitHubReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L,
    @SerialName("content_type") val contentType: String = ""
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val body: String = "",
    val assets: List<GitHubReleaseAsset> = emptyList()
)

interface GitHubReleasesApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latest(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease
}
