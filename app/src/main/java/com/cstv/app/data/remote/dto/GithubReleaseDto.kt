package com.cstv.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Réponse minimale de `GET repos/scilone/cstv/releases/latest` (F35, §4.4). */
data class GithubReleaseDto(
    @SerializedName("tag_name") val tagName: String?,
    @SerializedName("draft") val draft: Boolean?,
    @SerializedName("prerelease") val prerelease: Boolean?,
    @SerializedName("assets") val assets: List<GithubReleaseAssetDto>?
)

data class GithubReleaseAssetDto(
    @SerializedName("name") val name: String?,
    @SerializedName("browser_download_url") val browserDownloadUrl: String?,
    @SerializedName("size") val size: Long?
)
