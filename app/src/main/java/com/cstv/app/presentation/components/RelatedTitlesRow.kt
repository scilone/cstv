package com.cstv.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface3

/**
 * Rangée horizontale « Titres associés » réutilisée par les écrans de détails
 * VOD et Séries. Chaque item est un poster cliquable (2:3), focusable au D-pad.
 * Ne s'affiche pas si [items] est vide (le caller ne l'appelle alors pas).
 *
 * [tvPivotEnabled] câble le dispositif TV commun au reste de l'application
 * (F19/F23/B22) : le cadre de focus reste fixe sur l'emplacement de la première
 * vignette et c'est la rangée qui défile dessous. Le paramètre est opt-in pour
 * que le chemin mobile et la fiche série gardent leur anneau de focus par
 * vignette tant qu'ils n'ont pas été convertis.
 */
@Composable
fun <T> RelatedTitlesRow(
    title: String,
    items: List<T>,
    poster: (T) -> String?,
    label: (T) -> String,
    onClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    tvPivotEnabled: Boolean = false,
    firstItemFocusRequester: FocusRequester? = null,
    state: LazyListState = rememberLazyListState()
) {
    val lastIndex = items.lastIndex
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().focusGroup()
        ) {
            itemsIndexed(items) { index, item ->
                Box(
                    modifier = Modifier
                        .tvPivotItem(
                            enabled = tvPivotEnabled,
                            state = state,
                            index = index,
                            selectorCornerRadius = 12.dp
                        )
                        .then(
                            if (firstItemFocusRequester != null && index == 0) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        // Aux deux extrémités, la recherche de focus par défaut
                        // sort de la rangée et va chercher le focalisable le
                        // plus proche ailleurs dans l'arbre — les étiquettes de
                        // crédits, posées plus haut à droite. Le focus quittait
                        // donc la rangée sur une simple pression vers la droite
                        // en fin de liste. `Cancel` fait de la butée un
                        // non-événement, comportement attendu en bout de rangée.
                        .then(
                            if (!tvPivotEnabled) {
                                Modifier
                            } else {
                                Modifier.focusProperties {
                                    if (index == 0) left = FocusRequester.Cancel
                                    if (index == lastIndex) right = FocusRequester.Cancel
                                }
                            }
                        )
                ) {
                    RelatedTitleCard(
                        cover = poster(item),
                        name = label(item),
                        onClick = { onClick(item) }
                    )
                }
            }
            // Sans cette réserve, la dernière vignette ne peut pas rejoindre
            // l'ancre de début et le cadre y sauterait.
            tvPivotHorizontalEndSpacer(tvPivotEnabled)
        }
    }
}

@Composable
private fun RelatedTitleCard(
    cover: String?,
    name: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(110.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .tvFocusHighlight(isFocused, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(Surface3)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(Surface1),
            contentAlignment = Alignment.Center
        ) {
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.DarkGray,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
