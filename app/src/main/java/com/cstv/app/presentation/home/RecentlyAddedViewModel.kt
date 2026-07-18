package com.cstv.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.domain.repository.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentlyAddedState(
    val isLoading: Boolean = false,
    val vodStreams: List<VodStream> = emptyList(),
    val seriesStreams: List<SeriesStream> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class RecentlyAddedViewModel @Inject constructor(
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RecentlyAddedState())
    val state: StateFlow<RecentlyAddedState> = _state.asStateFlow()

    fun loadRecentlyAdded(isSeries: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                if (isSeries) {
                    val allSeries = seriesRepository.getSeriesStreams("all", forceRefresh = false)
                    val hiddenCategories = getHiddenCategoryIds(CategoryType.SERIES)
                    val recent = allSeries
                        .filter { it.categoryId !in hiddenCategories }
                        .sortedByDescending { it.added?.toLongOrNull() ?: 0L }
                        .take(100)
                    _state.update {
                        it.copy(isLoading = false, seriesStreams = recent, vodStreams = emptyList())
                    }
                } else {
                    val allVod = vodRepository.getVodStreams("all", forceRefresh = false)
                    val hiddenCategories = getHiddenCategoryIds(CategoryType.VOD)
                    val recent = allVod
                        .filter { it.categoryId !in hiddenCategories }
                        .sortedByDescending { it.added?.toLongOrNull() ?: 0L }
                        .take(100)
                    _state.update {
                        it.copy(isLoading = false, vodStreams = recent, seriesStreams = emptyList())
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Erreur lors du chargement des derniers ajouts")
                }
            }
        }
    }

    private suspend fun getHiddenCategoryIds(type: CategoryType): Set<String> {
        return try {
            categoryPreferenceRepository.getPreferences(type)
                .filterValues { it.hidden }
                .keys
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptySet()
        }
    }
}
