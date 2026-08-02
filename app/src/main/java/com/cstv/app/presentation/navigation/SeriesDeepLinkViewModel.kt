package com.cstv.app.presentation.navigation

import androidx.lifecycle.ViewModel
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.repository.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Résolution du deep link de notification "nouveaux épisodes" (F12) :
 * lecture strictement locale (cache Room), pour que le clic fonctionne aussi
 * hors ligne et qu'une série disparue du catalogue soit ignorée silencieusement.
 */
@HiltViewModel
class SeriesDeepLinkViewModel @Inject constructor(
    private val seriesRepository: SeriesRepository
) : ViewModel() {
    suspend fun resolveSeries(seriesId: Int): SeriesStream? = seriesRepository.getStreamById(seriesId)
}
