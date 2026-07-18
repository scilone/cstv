package com.poc.iptvxtream.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poc.iptvxtream.domain.model.AdvancedSearchFilter
import com.poc.iptvxtream.domain.model.CategoryWithCount
import com.poc.iptvxtream.domain.model.SearchMediaType
import com.poc.iptvxtream.presentation.theme.AccentLavande
import com.poc.iptvxtream.presentation.theme.BricolageGrotesque
import com.poc.iptvxtream.presentation.theme.HankenGrotesk
import com.poc.iptvxtream.presentation.theme.Surface1
import com.poc.iptvxtream.presentation.theme.Surface2
import com.poc.iptvxtream.presentation.theme.Surface3
import com.poc.iptvxtream.presentation.theme.TextPrimary
import com.poc.iptvxtream.presentation.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Bottom sheet « Recherche avancée » (mobile), iso-maquettes
 * docs/design-reference/screenshots/advanced-search-{filters-open-empty,
 * filters-open-some-selected,type-none,category-closed,category-open}.png.
 *
 * Composable stateless (state hoisting) : tout l'état vient de [filter] /
 * [availableGenres] / [availableCategories] / [resultCount] et chaque
 * interaction remonte via un callback. Aucune logique métier ici.
 *
 * Type de média = choix **exclusif** (Film XOR Série, ou aucun). Tant qu'aucun
 * type n'est choisi, le dropdown catégorie est désactivé (« Choisir un type
 * d'abord »).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedSearchSheet(
    filter: AdvancedSearchFilter,
    availableGenres: List<String>,
    availableCategories: List<CategoryWithCount>,
    resultCount: Int,
    onMediaTypeSelected: (SearchMediaType?) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onMinRatingSelected: (Int?) -> Unit,
    onYearRangeChanged: (IntRange) -> Unit,
    onGenreToggled: (String) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var categoryExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface2,
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            // --- En-tête ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                Text(
                    text = "Recherche avancée",
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.01).sp,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Réinitialiser",
                    fontFamily = HankenGrotesk,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = AccentLavande,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            categoryExpanded = false
                            onReset()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // --- Catégorie du média (choix exclusif) ---
            SectionLabel("CATÉGORIE DU MÉDIA")
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                MediaTypeChip(
                    label = "Film",
                    selected = filter.mediaType == SearchMediaType.FILM,
                    onClick = {
                        categoryExpanded = false
                        onMediaTypeSelected(
                            if (filter.mediaType == SearchMediaType.FILM) null else SearchMediaType.FILM
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                MediaTypeChip(
                    label = "Série",
                    selected = filter.mediaType == SearchMediaType.SERIE,
                    onClick = {
                        categoryExpanded = false
                        onMediaTypeSelected(
                            if (filter.mediaType == SearchMediaType.SERIE) null else SearchMediaType.SERIE
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // --- Dropdown catégorie (dépend du type) ---
            CategoryDropdown(
                enabled = filter.mediaType != null,
                selectedCategoryId = filter.categoryId,
                categories = availableCategories,
                expanded = categoryExpanded,
                onToggleExpanded = { categoryExpanded = !categoryExpanded },
                onSelect = { id ->
                    categoryExpanded = false
                    // "all" (Toutes les catégories) = pas de filtre catégorie.
                    onCategorySelected(if (id == "all") null else id)
                }
            )

            Spacer(Modifier.height(20.dp))

            // --- Note minimum ---
            SectionLabel("NOTE MINIMUM")
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                RatingChip("Toutes", filter.minRating == null, { onMinRatingSelected(null) }, Modifier.weight(1f))
                listOf(6, 7, 8, 9).forEach { r ->
                    RatingChip("$r+", filter.minRating == r, { onMinRatingSelected(r) }, Modifier.weight(1f))
                }
            }

            // --- Année de sortie ---
            YearRangeSection(
                yearRange = filter.yearRange ?: (AdvancedSearchFilter.DEFAULT_MIN_YEAR..AdvancedSearchFilter.DEFAULT_MAX_YEAR),
                onYearRangeChanged = onYearRangeChanged
            )

            Spacer(Modifier.height(20.dp))

            // --- Genres (top 20) ---
            if (availableGenres.isNotEmpty()) {
                SectionLabel("GENRES")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    availableGenres.forEach { genre ->
                        GenreChip(
                            label = genre,
                            selected = genre in filter.genres,
                            onClick = { onGenreToggled(genre) }
                        )
                    }
                }
            }

            // --- Bouton résultats ---
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AccentLavande)
                    .clickable { onApply() }
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = "Voir les résultats ($resultCount)",
                    fontFamily = HankenGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF17131F)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.72.sp,
        color = TextSecondary,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun MediaTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AccentLavande else Surface3)
            .border(
                1.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = label,
            fontFamily = HankenGrotesk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = if (selected) Color(0xFF17131F) else TextPrimary
        )
    }
}

@Composable
private fun CategoryDropdown(
    enabled: Boolean,
    selectedCategoryId: String?,
    categories: List<CategoryWithCount>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelect: (String) -> Unit
) {
    val label = when {
        !enabled -> "Choisir un type d'abord"
        selectedCategoryId == null -> "Toutes les catégories"
        else -> categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Toutes les catégories"
    }
    val isOpen = enabled && expanded

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .then(if (enabled) Modifier.clickable { onToggleExpanded() } else Modifier)
                .background(Surface3)
                .border(
                    1.dp,
                    if (isOpen) AccentLavande.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Text(
                text = label,
                color = if (enabled) TextPrimary else TextSecondary,
                fontFamily = HankenGrotesk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        // Liste déroulante inline (façon maquette category-open).
        if (isOpen) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface1)
                    .padding(vertical = 4.dp)
            ) {
                items(categories, key = { it.id }) { cat ->
                    val isSelected = (cat.id == "all" && selectedCategoryId == null) || cat.id == selectedCategoryId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(cat.id) }
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                    ) {
                        Text(
                            text = cat.name,
                            fontFamily = HankenGrotesk,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            color = if (isSelected) Color(0xFFF4F4F7) else Color(0xFFC7C7D1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = cat.count.toString(),
                            fontFamily = HankenGrotesk,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = Color(0xFF63636F)
                        )
                        if (isSelected) {
                            Spacer(Modifier.width(10.dp))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = AccentLavande,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AccentLavande else Surface3)
            .border(
                1.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 14.dp)
    ) {
        Text(
            text = label,
            fontFamily = HankenGrotesk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (selected) Color(0xFF17131F) else TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun GenreChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) AccentLavande else Surface3)
            .border(
                1.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontFamily = HankenGrotesk,
            fontWeight = FontWeight.Medium,
            fontSize = 13.5.sp,
            color = if (selected) Color(0xFF17131F) else TextPrimary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearRangeSection(
    yearRange: IntRange,
    onYearRangeChanged: (IntRange) -> Unit
) {
    val min = AdvancedSearchFilter.DEFAULT_MIN_YEAR
    val max = AdvancedSearchFilter.DEFAULT_MAX_YEAR
    val isFullRange = yearRange.first <= min && yearRange.last >= max

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    ) {
        Text(
            text = "ANNÉE DE SORTIE",
            fontFamily = HankenGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 0.72.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (isFullRange) "Toutes les années" else "${yearRange.first} – ${yearRange.last}",
            fontFamily = HankenGrotesk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = AccentLavande,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onYearRangeChanged(min..max) }
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }

    var sliderValues by remember(yearRange) {
        mutableStateOf(yearRange.first.toFloat()..yearRange.last.toFloat())
    }
    RangeSlider(
        value = sliderValues,
        onValueChange = { sliderValues = it },
        onValueChangeFinished = {
            onYearRangeChanged(sliderValues.start.roundToInt()..sliderValues.endInclusive.roundToInt())
        },
        valueRange = min.toFloat()..max.toFloat(),
        steps = (max - min - 1).coerceAtLeast(0),
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = AccentLavande,
            inactiveTrackColor = Color.White.copy(alpha = 0.12f)
        ),
        modifier = Modifier.fillMaxWidth()
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(min.toString(), fontFamily = HankenGrotesk, fontSize = 12.sp, color = TextSecondary)
        Text(max.toString(), fontFamily = HankenGrotesk, fontSize = 12.sp, color = TextSecondary)
    }
}
