package com.cstv.app.domain.util

/** Horloge injectable afin de tester les expirations de cache sans délai réel. */
interface TimeProvider {
    fun nowMillis(): Long
}
