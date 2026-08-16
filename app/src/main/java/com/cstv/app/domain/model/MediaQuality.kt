package com.cstv.app.domain.model

/** Stable quality buckets, ordered from least to most useful. */
enum class MediaQuality(val storageCode: String, val rank: Int) {
    SD("sd", 10),
    HD("hd", 20),
    FHD("fhd", 30),
    UHD_4K("uhd_4k", 40)
}
