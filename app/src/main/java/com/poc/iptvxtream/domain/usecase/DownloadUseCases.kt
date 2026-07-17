package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.DownloadRequestData
import com.poc.iptvxtream.domain.model.DownloadedItem
import com.poc.iptvxtream.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDownloadsUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    operator fun invoke(): Flow<List<DownloadedItem>> = repository.observeDownloads()
}

class StartDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(data: DownloadRequestData) = repository.startDownload(data)
}

class RemoveDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(contentId: String) = repository.removeDownload(contentId)
}

class GetDownloadsUsedBytesUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(): Long = repository.getUsedBytes()
}
