package com.cstv.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cstv.app.R

/**
 * Libellé affiché sur une fiche média pour une classification exacte (F45 §8.13, ex. 13/15/17).
 * `0` (ou toute valeur non positive) signifie « tout public » : l'afficher via `media_age_rating`
 * produirait "0 ans", trompeur — TP est le libellé attendu.
 */
@Composable
fun Int.displayLabel(): String =
    if (this <= 0) stringResource(R.string.media_age_rating_all)
    else stringResource(R.string.media_age_rating, this)
