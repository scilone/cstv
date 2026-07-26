package com.cstv.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.LiveTvRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Une catégorie affichée dans l'écran de gestion, avec son état de masquage. */
data class ManagedCategory(
    val categoryId: String,
    val categoryName: String,
    val hidden: Boolean
)

data class CategoryManagementState(
    val selectedType: CategoryType = CategoryType.LIVE,
    val isLoading: Boolean = false,
    val categories: List<ManagedCategory> = emptyList(),
    val error: String? = null
)

/**
 * Gestion des catégories par profil (Phase 58) : liste complète (y compris
 * masquées) dans l'ordre effectif du profil actif, avec masquage et
 * réordonnancement par boutons haut/bas (adapté au D-pad TV).
 */
@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val liveTvRepository: LiveTvRepository,
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CategoryManagementState())
    val state: StateFlow<CategoryManagementState> = _state.asStateFlow()

    init {
        loadCategories(CategoryType.LIVE)
    }

    fun selectType(type: CategoryType) {
        if (type == _state.value.selectedType && _state.value.categories.isNotEmpty()) return
        loadCategories(type)
    }

    private fun loadCategories(type: CategoryType) {
        viewModelScope.launch {
            _state.update { it.copy(selectedType = type, isLoading = true, error = null) }
            try {
                // Liste complète non filtrée (les use cases de grille excluent les
                // masquées) : on passe par les repositories, puis on applique
                // uniquement l'ordre personnalisé.
                val raw = when (type) {
                    CategoryType.LIVE -> liveTvRepository.getCachedLiveCategories()
                        .map { it.categoryId to it.categoryName }
                    CategoryType.VOD -> vodRepository.getCachedVodCategories()
                        .map { it.categoryId to it.categoryName }
                    CategoryType.SERIES -> seriesRepository.getCachedSeriesCategories()
                        .map { it.categoryId to it.categoryName }
                }
                val prefs = categoryPreferenceRepository.getPreferences(type)
                val ordered = raw.withIndex()
                    .sortedWith(
                        compareBy(
                            { prefs[it.value.first]?.sortOrder ?: it.index },
                            { it.index }
                        )
                    )
                    .map { (_, category) ->
                        ManagedCategory(
                            categoryId = category.first,
                            categoryName = category.second,
                            hidden = prefs[category.first]?.hidden == true
                        )
                    }
                _state.update { it.copy(isLoading = false, categories = ordered) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Impossible de charger les catégories. Vérifiez votre connexion."
                    )
                }
            }
        }
    }

    fun toggleHidden(categoryId: String) {
        val current = _state.value
        val target = current.categories.find { it.categoryId == categoryId } ?: return
        val newHidden = !target.hidden
        _state.update { state ->
            state.copy(
                categories = state.categories.map {
                    if (it.categoryId == categoryId) it.copy(hidden = newHidden) else it
                }
            )
        }
        viewModelScope.launch {
            categoryPreferenceRepository.setHidden(current.selectedType, categoryId, newHidden)
        }
    }

    fun moveUp(categoryId: String) = move(categoryId, -1)

    fun moveDown(categoryId: String) = move(categoryId, +1)

    private fun move(categoryId: String, delta: Int) {
        val current = _state.value
        val index = current.categories.indexOfFirst { it.categoryId == categoryId }
        val newIndex = index + delta
        if (index < 0 || newIndex < 0 || newIndex >= current.categories.size) return

        val reordered = current.categories.toMutableList().apply {
            add(newIndex, removeAt(index))
        }
        _state.update { it.copy(categories = reordered) }
        viewModelScope.launch {
            categoryPreferenceRepository.saveOrder(
                current.selectedType,
                reordered.map { it.categoryId }
            )
        }
    }
}
