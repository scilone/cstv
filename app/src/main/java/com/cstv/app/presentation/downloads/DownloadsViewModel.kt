package com.cstv.app.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.domain.model.DownloadRequestFactory
import com.cstv.app.domain.model.DownloadedItem
import com.cstv.app.domain.model.SeriesEpisode
import com.cstv.app.domain.model.VodDetails
import com.cstv.app.domain.usecase.GetDownloadsUsedBytesUseCase
import com.cstv.app.domain.usecase.ObserveDownloadsUseCase
import com.cstv.app.domain.usecase.RemoveDownloadUseCase
import com.cstv.app.domain.usecase.StartDownloadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val downloads: List<DownloadedItem> = emptyList(),
    val usedBytes: Long = 0L
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val observeDownloadsUseCase: ObserveDownloadsUseCase,
    private val startDownloadUseCase: StartDownloadUseCase,
    private val removeDownloadUseCase: RemoveDownloadUseCase,
    private val getDownloadsUsedBytesUseCase: GetDownloadsUsedBytesUseCase,
    private val credentialsManager: CredentialsManager
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadsUiState())
    val state: StateFlow<DownloadsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeDownloadsUseCase().collect { downloads ->
                _state.update { it.copy(downloads = downloads) }
                refreshUsedBytes()
            }
        }
    }

    private fun refreshUsedBytes() {
        viewModelScope.launch {
            val bytes = runCatching { getDownloadsUsedBytesUseCase() }.getOrDefault(0L)
            _state.update { it.copy(usedBytes = bytes) }
        }
    }

    /** État de téléchargement d'un film (ou null si non téléchargé). */
    fun movieDownload(streamId: Int): DownloadedItem? =
        _state.value.downloads.firstOrNull { it.contentId == DownloadedItem.movieContentId(streamId) }

    /** État de téléchargement d'un épisode (ou null). */
    fun episodeDownload(episodeId: Int): DownloadedItem? =
        _state.value.downloads.firstOrNull { it.contentId == DownloadedItem.episodeContentId(episodeId) }

    fun downloadMovie(details: VodDetails) {
        val creds = credentialsManager.getCredentials() ?: return
        viewModelScope.launch {
            startDownloadUseCase(
                DownloadRequestFactory.forMovie(details, creds.baseUrl, creds.username, creds.password)
            )
        }
    }

    fun downloadEpisode(episode: SeriesEpisode, seriesId: Int, seriesTitle: String, cover: String?) {
        val creds = credentialsManager.getCredentials() ?: return
        viewModelScope.launch {
            startDownloadUseCase(
                DownloadRequestFactory.forEpisode(episode, seriesId, seriesTitle, cover, creds.baseUrl, creds.username, creds.password)
            )
        }
    }

    fun remove(contentId: String) {
        viewModelScope.launch { removeDownloadUseCase(contentId) }
    }
}
