package com.cstv.app.domain.model

/** Stable quality buckets, ordered from least to most useful. */
enum class MediaQuality(val storageCode: String, val rank: Int) {
    SD("sd", 10),
    HD("hd", 20),
    FHD("fhd", 30),
    UHD_4K("uhd_4k", 40)
}

/**
 * F39 §8.2 : rang qualité d'une version, pour trier les entrées d'un même
 * `linkKey` (qualité décroissante). `0` pour un tag absent ou non reconnu —
 * une version sans qualité détectée ne doit jamais dominer ni fausser le tri,
 * elle finit simplement en queue.
 */
fun mediaQualityRank(qualityTag: String?): Int =
    MediaQuality.entries.firstOrNull { it.storageCode == qualityTag }?.rank ?: 0
