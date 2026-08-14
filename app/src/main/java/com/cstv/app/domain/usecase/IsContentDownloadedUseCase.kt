package com.cstv.app.domain.usecase

import com.cstv.app.data.local.dao.DownloadDao
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
import com.cstv.app.domain.model.DownloadContentId
import com.cstv.app.domain.model.DownloadStatus
import javax.inject.Inject

/** Single source of truth for the "local media bypasses the Xtream lock" rule. */
class IsContentDownloadedUseCase @Inject constructor(
    private val downloads: DownloadDao,
    private val accountKeyProvider: CurrentAccountKeyProvider,
) {
    suspend operator fun invoke(contentId: String?): Boolean {
        val (kind, providerId) = contentId?.let(DownloadContentId::parse) ?: return false
        return downloads.getByRef(accountKeyProvider.current(), kind.storageValue, providerId)?.status == DownloadStatus.COMPLETED.name
    }
}
