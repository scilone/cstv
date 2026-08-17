package com.cstv.app.data.repository

import com.cstv.app.data.local.entity.LiveStreamEntity
import com.cstv.app.domain.model.LiveStream

internal fun LiveStreamEntity.toDomain() = LiveStream(
    streamId, name, streamIcon, epgChannelId, num, categoryId,
    cleanTitle, linkKey, languageTag, languageRaw, qualityTag, qualityRaw
)
