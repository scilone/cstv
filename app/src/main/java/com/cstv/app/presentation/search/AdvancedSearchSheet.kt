package com.cstv.app.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cstv.app.domain.model.AdvancedSearchFilter
import com.cstv.app.domain.model.CategoryWithCount
import com.cstv.app.domain.model.SearchMediaType
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.BricolageGrotesque
import com.cstv.app.presentation.theme.HankenGrotesk
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface2
import com.cstv.app.presentation.theme.Surface3
import com.cstv.app.presentation.theme.TextPrimary
import com.cstv.app.presentation.theme.TextSecondary
import kotlin.math.roundToInt

// Couleur de bordure de focus D-pad, iso reste de l'app (SearchGridCard,
// SearchCardItem) : accent lavande, contraste WCAG AA sur fond Surface3/1.
private val FocusRingColor = AccentLavande

/**
 * Bottom sheet « Recherche avancée », iso-maquettes
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
 *
 * [isTv] adapte la navigation D-pad : chaque contrôle devient focusable avec
 * un anneau de focus contrasté, et le RangeSlider (non focusable/traversable
 * de façon fiable au D-pad, et absent de tv-material) est remplacé par des
 * steppers +/- pour la borne min et la borne max de l'année de sortie.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedSearchSheet(
    filter: AdvancedSearchFilter,
    availableGenres: List<String>,
    availableCategories: List<CategoryWithCount>,
    resultCount: Int,
    catalogYearRange: IntRange,
    isTv: Boolean = false,
    showMediaTypeFilter: Boolean = true,
    showCategoryFilter: Boolean = true,
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
                .focusGroup()
        ) {
            // La zone des filtres défile indépendamment du bouton d'application.
            // fill = false conserve la hauteur naturelle de la sheet si le
            // contenu est court, tout en la plafonnant si les filtres sont longs.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
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
                    FocusableLink(
                        text = "Réinitialiser",
                        onClick = {
                            categoryExpanded = false
                            onReset()
                        }
                    )
                }

                // In a category screen, type and category are fixed by context.
                if (showMediaTypeFilter) {
                    SectionLabel("CATÉGORIE DU MÉDIA")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        MediaTypeChip(
                            label = "Film",
                            selected = filter.mediaType == SearchMediaType.FILM,
                            isTv = isTv,
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
                            isTv = isTv,
                            onClick = {
                                categoryExpanded = false
                                onMediaTypeSelected(
                                    if (filter.mediaType == SearchMediaType.SERIE) null else SearchMediaType.SERIE
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // --- Dropdown catégorie (dépend du type) ---
                if (showCategoryFilter) {
                    CategoryDropdown(
                        enabled = filter.mediaType != null,
                        selectedCategoryId = filter.categoryId,
                        categories = availableCategories,
                        expanded = categoryExpanded,
                        isTv = isTv,
                        onToggleExpanded = { categoryExpanded = !categoryExpanded },
                        onSelect = { id ->
                            categoryExpanded = false
                            // "all" (Toutes les catégories) = pas de filtre catégorie.
                            onCategorySelected(if (id == "all") null else id)
                        }
                    )
                }

                Spacer(Modifier.height(20.dp))

                // --- Note minimum ---
                SectionLabel("NOTE MINIMUM")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                ) {
                    RatingChip("Toutes", filter.minRating == null, isTv, { onMinRatingSelected(null) }, Modifier.weight(1f))
                    listOf(7, 8, 9).forEach { r ->
                        RatingChip("$r+", filter.minRating == r, isTv, { onMinRatingSelected(r) }, Modifier.weight(1f))
                    }
                    RatingChip("10", filter.minRating == 10, isTv, { onMinRatingSelected(10) }, Modifier.weight(1f))
                }

                // --- Année de sortie ---
                YearRangeSection(
                    yearRange = filter.yearRange ?: catalogYearRange,
                    catalogYearRange = catalogYearRange,
                    isTv = isTv,
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
                                isTv = isTv,
                                onClick = { onGenreToggled(genre) }
                            )
                        }
                    }
                }
            }

            // --- Bouton résultats ---
            var applyFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { applyFocused = it.isFocused }
                        .clip(RoundedCornerShape(16.dp))
                        .background(AccentLavande)
                        .then(
                            if (isTv) Modifier.border(
                                3.dp,
                                if (applyFocused) Color.White else Color.Transparent,
                                RoundedCornerShape(16.dp)
                            ) else Modifier
                        )
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
private fun FocusableLink(text: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Text(
        text = text,
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = if (isFocused) Color.White else AccentLavande,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (isFocused) Modifier.background(AccentLavande.copy(alpha = 0.25f)) else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

/** Bordure de focus D-pad commune à tous les contrôles de la sheet (TV uniquement). */
private fun Modifier.tvFocusRing(isTv: Boolean, isFocused: Boolean, shape: RoundedCornerShape) =
    if (isTv) this.border(3.dp, if (isFocused) FocusRingColor else Color.Transparent, shape) else this

@Composable
private fun MediaTypeChip(
    label: String,
    selected: Boolean,
    isTv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(shape)
            .background(if (selected) AccentLavande else Surface3)
            .border(
                1.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.10f),
                shape
            )
            .tvFocusRing(isTv, isFocused, shape)
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
    isTv: Boolean,
    onToggleExpanded: () -> Unit,
    onSelect: (String) -> Unit
) {
    val label = when {
        !enabled -> "Choisir un type d'abord"
        selectedCategoryId == null -> "Toutes les catégories"
        else -> categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Toutes les catégories"
    }
    val isOpen = enabled && expanded
    var triggerFocused by remember { mutableStateOf(false) }
    val triggerShape = RoundedCornerShape(12.dp)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { triggerFocused = it.isFocused }
                .clip(triggerShape)
                .then(if (enabled) Modifier.clickable { onToggleExpanded() } else Modifier)
                .background(Surface3)
                .border(
                    1.dp,
                    if (isOpen) AccentLavande.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f),
                    triggerShape
                )
                .tvFocusRing(isTv && enabled, triggerFocused, triggerShape)
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

        // Liste déroulante inline (façon maquette category-open), focusable au D-pad.
        if (isOpen) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface1)
                    .padding(vertical = 4.dp)
                    .focusGroup()
            ) {
                items(categories, key = { it.id }) { cat ->
                    val isSelected = (cat.id == "all" && selectedCategoryId == null) || cat.id == selectedCategoryId
                    var rowFocused by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { rowFocused = it.isFocused }
                            .then(
                                if (isTv && rowFocused) Modifier.background(AccentLavande.copy(alpha = 0.18f)) else Modifier
                            )
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
    isTv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(shape)
            .background(if (selected) AccentLavande else Surface3)
            .border(
                1.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.08f),
                shape
            )
            .tvFocusRing(isTv, isFocused, shape)
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
    isTv: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(shape)
            .background(if (selected) AccentLavande else Surface3)
            .border(
                1.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.10f),
                shape
            )
            .tvFocusRing(isTv, isFocused, shape)
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
    catalogYearRange: IntRange,
    isTv: Boolean,
    onYearRangeChanged: (IntRange) -> Unit
) {
    val min = catalogYearRange.first
    val max = catalogYearRange.last
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
        FocusableLink(
            text = if (isFullRange) "Toutes les années" else "${yearRange.first} – ${yearRange.last}",
            onClick = { onYearRangeChanged(min..max) }
        )
    }

    if (isTv) {
        // Le RangeSlider Compose n'est pas traversable de façon fiable au D-pad
        // et tv-material (alpha10) ne fournit pas de composant Slider : on
        // utilise deux steppers +/- (année min, année max) à la place.
        YearStepperRow(
            label = "De",
            year = yearRange.first,
            min = min,
            max = yearRange.last,
            onDecrement = { onYearRangeChanged((yearRange.first - 1).coerceAtLeast(min)..yearRange.last) },
            onIncrement = { onYearRangeChanged((yearRange.first + 1).coerceAtMost(yearRange.last)..yearRange.last) }
        )
        Spacer(Modifier.height(10.dp))
        YearStepperRow(
            label = "À",
            year = yearRange.last,
            min = yearRange.first,
            max = max,
            onDecrement = { onYearRangeChanged(yearRange.first..(yearRange.last - 1).coerceAtLeast(yearRange.first)) },
            onIncrement = { onYearRangeChanged(yearRange.first..(yearRange.last + 1).coerceAtMost(max)) }
        )
    } else {
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
}

@Composable
private fun YearStepperRow(
    label: String,
    year: Int,
    min: Int,
    max: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontFamily = HankenGrotesk,
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.width(24.dp)
        )
        StepperButton(icon = Icons.Default.Remove, enabled = year > min, onClick = onDecrement)
        Text(
            text = year.toString(),
            fontFamily = HankenGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        StepperButton(icon = Icons.Default.Add, enabled = year < max, onClick = onIncrement)
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(shape)
            .background(Surface3)
            .border(3.dp, if (isFocused) FocusRingColor else Color.Transparent, shape)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )
    }
}
