package com.poc.iptvxtream.presentation.settings

import androidx.lifecycle.ViewModel
import com.poc.iptvxtream.data.local.storage.CategorySorting
import com.poc.iptvxtream.data.local.storage.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _state.update {
            it.copy(
                tvSorting = settingsManager.getTvCategorySorting(),
                vodSorting = settingsManager.getVodCategorySorting(),
                seriesSorting = settingsManager.getSeriesCategorySorting()
            )
        }
    }

    fun updateTvSorting(sorting: CategorySorting) {
        settingsManager.setTvCategorySorting(sorting)
        _state.update { it.copy(tvSorting = sorting) }
    }

    fun updateVodSorting(sorting: CategorySorting) {
        settingsManager.setVodCategorySorting(sorting)
        _state.update { it.copy(vodSorting = sorting) }
    }

    fun updateSeriesSorting(sorting: CategorySorting) {
        settingsManager.setSeriesCategorySorting(sorting)
        _state.update { it.copy(seriesSorting = sorting) }
    }
}
