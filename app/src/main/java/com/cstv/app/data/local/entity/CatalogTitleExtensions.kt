package com.cstv.app.data.local.entity

import com.cstv.app.domain.model.ParsedMediaTitle

/** Single mapping between T21's domain parse result and Room storage fields. */
fun LiveStreamEntity.withParsedTitle(parsed: ParsedMediaTitle): LiveStreamEntity = copy(
    cleanTitle = parsed.cleanTitle, linkKey = parsed.linkKey,
    languageTag = parsed.language?.storageCode, languageRaw = parsed.languageRaw,
    qualityTag = parsed.quality?.storageCode, qualityRaw = parsed.qualityRaw
)

fun VodStreamEntity.withParsedTitle(parsed: ParsedMediaTitle): VodStreamEntity = copy(
    cleanTitle = parsed.cleanTitle, linkKey = parsed.linkKey,
    languageTag = parsed.language?.storageCode, languageRaw = parsed.languageRaw,
    qualityTag = parsed.quality?.storageCode, qualityRaw = parsed.qualityRaw
)

fun SeriesStreamEntity.withParsedTitle(parsed: ParsedMediaTitle): SeriesStreamEntity = copy(
    cleanTitle = parsed.cleanTitle, linkKey = parsed.linkKey,
    languageTag = parsed.language?.storageCode, languageRaw = parsed.languageRaw,
    qualityTag = parsed.quality?.storageCode, qualityRaw = parsed.qualityRaw
)
