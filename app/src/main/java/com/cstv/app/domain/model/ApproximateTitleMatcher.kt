package com.cstv.app.domain.model

object ApproximateTitleMatcher {

    fun computeSimilarity(tmdbTitle: String, iptvTitle: String): Double {
        val s1 = TitleNormalizer.normalize(tmdbTitle)
        val s2 = TitleNormalizer.normalize(iptvTitle)
        return computeSimilarityNormalized(s1, s2)
    }

    fun computeSimilarityNormalized(s1: String, s2: String): Double {
        if (s1.isBlank() || s2.isBlank()) return 0.0
        if (s1 == s2) return 1.0

        // Word-boundary containment (e.g., "dragon ball" matches "dragon ball z")
        val s1Words = s1.split(" ")
        val s2Words = s2.split(" ")

        // Containment "mots" : le titre TMDB propre apparaît comme suite CONTIGUË
        // de mots dans le titre IPTV (ex. "Dragon Ball Z" dans "Dragon Ball Z Saga
        // Cell"). Deux garde-fous contre les faux positifs :
        //  - contiguïté (et non simple sous-séquence) : "Iron Man" ne matche pas
        //    "Iron Fist Man" ;
        //  - borne de longueur : le titre IPTV ne peut dépasser 2× le nombre de
        //    mots TMDB, sinon "The Hunt" matcherait "The Hunt for Red October".
        if (s1Words.size > 1 &&
            s2Words.size >= s1Words.size &&
            s2Words.size <= s1Words.size * 2 &&
            containsContiguousWords(s2Words, s1Words)
        ) {
            return 0.9
        }

        // Fallback to Levenshtein similarity
        val distance = levenshtein(s1, s2)
        val maxLength = maxOf(s1.length, s2.length)
        return 1.0 - (distance.toDouble() / maxLength)
    }

    /** Vrai si [sub] apparaît comme suite de mots contiguë dans [full]. */
    private fun containsContiguousWords(full: List<String>, sub: List<String>): Boolean {
        if (sub.isEmpty() || sub.size > full.size) return false
        for (start in 0..full.size - sub.size) {
            var match = true
            for (i in sub.indices) {
                if (full[start + i] != sub[i]) {
                    match = false
                    break
                }
            }
            if (match) return true
        }
        return false
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length

        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1)

        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val replace = cost[j - 1] + match
                val insert = cost[j] + 1
                val delete = newCost[j - 1] + 1
                newCost[j] = minOf(minOf(insert, delete), replace)
            }
            val temp = cost
            cost = newCost
            newCost = temp
        }
        return cost[lhsLength]
    }
}
