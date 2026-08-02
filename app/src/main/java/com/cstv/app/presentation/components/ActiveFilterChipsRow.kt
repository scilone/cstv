package com.cstv.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cstv.app.R
import com.cstv.app.domain.model.AdvancedSearchFilter
import com.cstv.app.domain.model.CategoryWithCount
import com.cstv.app.domain.model.SearchMediaType
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.HankenGrotesk

/**
 * Chips de filtres actifs supprimables (×), sous la barre de recherche
 * (docs/design-reference/screenshots/advanced-search-result.png). Partagé
 * entre la Recherche globale (type de média + catégorie inclus) et les
 * catégories TV Films/Séries (F22, où type et catégorie sont implicites).
 */
@Composable
fun ActiveFilterChipsRow(
    filter: AdvancedSearchFilter,
    isTv: Boolean,
    onRemoveMinRating: () -> Unit,
    onRemoveYearRange: () -> Unit,
    onRemoveGenre: (String) -> Unit,
    modifier: Modifier = Modifier,
    availableCategories: List<CategoryWithCount> = emptyList(),
    onRemoveMediaType: () -> Unit = {},
    onRemoveCategory: () -> Unit = {}
) {
    val filmLabel = stringResource(R.string.search_movies)
    val seriesLabel = stringResource(R.string.search_series)

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().focusGroup()
    ) {
        filter.mediaType?.let { type ->
            val label = when (type) {
                SearchMediaType.FILM -> filmLabel
                SearchMediaType.SERIE -> seriesLabel
            }
            item { ActiveFilterChip(label = label, isTv = isTv, onRemove = onRemoveMediaType) }
        }
        filter.categoryId?.let { categoryId ->
            val label = availableCategories.firstOrNull { it.id == categoryId }?.name ?: categoryId
            item { ActiveFilterChip(label = label, isTv = isTv, onRemove = onRemoveCategory) }
        }
        filter.minRating?.let { rating ->
            val label = if (rating >= 10) "Note 10" else "Note $rating+"
            item { ActiveFilterChip(label = label, isTv = isTv, onRemove = onRemoveMinRating) }
        }
        // yearRange non-null signifie déjà "resserré" : le VM normalise à null
        // toute sélection qui couvre tout le catalogue (voir setYearRange).
        filter.yearRange?.let { range ->
            item { ActiveFilterChip(label = "${range.first}–${range.last}", isTv = isTv, onRemove = onRemoveYearRange) }
        }
        items(filter.genres.toList()) { genre ->
            ActiveFilterChip(label = genre, isTv = isTv, onRemove = { onRemoveGenre(genre) })
        }
    }
}

/**
 * Chip de filtre actif supprimable. Sur mobile, seule la croix est cliquable
 * (précision au doigt). Sur TV, tout le chip est UNE seule cible de focus
 * D-pad — un focus dédié à la croix de 16dp serait trop petit/impraticable —
 * et la sélection (OK/Entrée) retire directement le filtre.
 */
@Composable
private fun ActiveFilterChip(
    label: String,
    isTv: Boolean,
    onRemove: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(shape)
            .background(AccentLavande.copy(alpha = 0.16f))
            .border(1.dp, AccentLavande.copy(alpha = 0.4f), shape)
            .then(
                if (isTv) Modifier.border(3.dp, if (isFocused) AccentLavande else Color.Transparent, shape)
                else Modifier
            )
            .then(if (isTv) Modifier.clickable { onRemove() } else Modifier)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Text(
            text = label,
            fontFamily = HankenGrotesk,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = AccentLavande,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.search_remove_filter),
            tint = AccentLavande,
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(50))
                .then(if (!isTv) Modifier.clickable { onRemove() } else Modifier)
        )
    }
}
