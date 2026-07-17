package com.poc.iptvxtream.domain.model

/**
 * Programme EPG en cours + programme suivant pour une chaîne (Phase 60, refonte
 * player). Purement informatif dans le player Live TV : aucune interaction
 * (pas de catch-up / timeshift, hors périmètre projet). Les deux champs peuvent
 * être null si l'EPG n'est pas disponible.
 */
data class LiveEpgNowNext(
    val current: LiveEpgProgram?,
    val next: LiveEpgProgram?
)
