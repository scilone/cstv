package com.cstv.app.domain.model

object ApproximateTitleMatcher {

    fun computeSimilarity(tmdbTitle: String, iptvTitle: String): Double {
        val s1 = TitleNormalizer.normalize(tmdbTitle)
        val s2 = TitleNormalizer.normalize(iptvTitle)

        if (s1.isBlank() || s2.isBlank()) return 0.0
        if (s1 == s2) return 1.0

        // Word-boundary containment (e.g., "dragon ball" matches "dragon ball z")
        val s1Words = s1.split(" ")
        val s2Words = s2.split(" ")

        if (s1Words.size > 1 && s2Words.size >= s1Words.size) {
            // Check if s1 words are fully contained in order inside s2
            if (isSubsequenceOfWords(s1Words, s2Words)) {
                return 0.9
            }
        }

        // Fallback to Levenshtein similarity
        val distance = levenshtein(s1, s2)
        val maxLength = maxOf(s1.length, s2.length)
        return 1.0 - (distance.toDouble() / maxLength)
    }

    private fun isSubsequenceOfWords(sub: List<String>, full: List<String>): Boolean {
        var subIndex = 0
        var fullIndex = 0
        while (subIndex < sub.size && fullIndex < full.size) {
            if (sub[subIndex] == full[fullIndex]) {
                subIndex++
            }
            fullIndex++
        }
        return subIndex == sub.size
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
