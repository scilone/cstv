package com.cstv.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cstv.app.R
import com.cstv.app.domain.model.AgeRating

/**
 * Libellé affiché sur une fiche média. `AgeRating.ALL` (valeur 0) signifie
 * « tout public » : l'afficher via `media_age_rating` produirait "0 ans",
 * trompeur — TP est le libellé attendu.
 */
@Composable
fun AgeRating.displayLabel(): String =
    if (this == AgeRating.ALL) stringResource(R.string.media_age_rating_all)
    else stringResource(R.string.media_age_rating, value)
