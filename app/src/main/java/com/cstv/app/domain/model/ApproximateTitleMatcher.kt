package com.cstv.app.domain.model

import kotlin.math.max

/**
 * Score de similarité entre deux titres **déjà normalisés** par
 * [TitleNormalizer]. Sert à rapprocher un titre TMDB propre d'un titre IPTV
 * sale sans faux positifs grossiers.
 *
 * Stratégie :
 *  - égalité stricte après normalisation → 1.0 (cas dominant attendu) ;
 *  - sinon ratio de Levenshtein caractère-à-caractère : `1 - dist / maxLen`.
 *
 * Le ratio caractère tolère fautes/variantes mineures (`spider man` ↔
 * `spiderman`, accents déjà aplanis en amont) tout en rejetant les préfixes
 * trompeurs : `war` vs `warrior` → 1 - 4/7 ≈ 0.43, sous le seuil.
 *
 * **Seuil retenu : 0.85.** Choisi empiriquement : assez haut pour écarter les
 * quasi-homonymes courts (`war`/`warrior`, `up`/`upgrade`), assez bas pour
 * absorber une lettre/espace de différence sur un titre de longueur moyenne.
 * Objet pur et testable — aucune dépendance Android.
 */
object ApproximateTitleMatcher {

    const val DEFAULT_THRESHOLD = 0.85

    /** Similarité 0.0–1.0 entre deux titres normalisés. */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val maxLen = max(a.length, b.length)
        val dist = levenshtein(a, b)
        return 1.0 - dist.toDouble() / maxLen
    }

    /** Vrai si [similarity] atteint [threshold]. */
    fun matches(a: String, b: String, threshold: Double = DEFAULT_THRESHOLD): Boolean =
        similarity(a, b) >= threshold

    // Distance de Levenshtein classique en O(n·m) mémoire O(min(n,m)),
    // suffisante pour des titres (quelques dizaines de caractères).
    private fun levenshtein(a: String, b: String): Int {
        val (s, t) = if (a.length <= b.length) a to b else b to a
        var prev = IntArray(s.length + 1) { it }
        var curr = IntArray(s.length + 1)
        for (j in 1..t.length) {
            curr[0] = j
            for (i in 1..s.length) {
                val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                curr[i] = minOf(
                    prev[i] + 1,        // suppression
                    curr[i - 1] + 1,    // insertion
                    prev[i - 1] + cost  // substitution
                )
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[s.length]
    }
}
