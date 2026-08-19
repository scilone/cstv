package com.cstv.app.domain.model

/**
 * Classification française fermée utilisée par le contrôle parental (F44),
 * alignée sur `ageRatingFr` exposé par T22 côté backend et sur
 * `Profile.maxAgeRating`/`profiles.max_age_rating` (0/10/12/16/18/null).
 */
enum class AgeRating(val value: Int) {
    ALL(0), TEN(10), TWELVE(12), SIXTEEN(16), EIGHTEEN(18);

    companion object {
        /**
         * `null` reste `null` (classification/niveau inconnu ou non bridé) : ne
         * jamais convertir une valeur absente en [ALL] (règle défensive F44).
         */
        fun fromValueOrNull(value: Int?): AgeRating? = value?.let { candidate ->
            entries.firstOrNull { it.value == candidate }
        }
    }
}
