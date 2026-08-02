package com.cstv.app.domain.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.TimeZone

class LiveEpgProgramTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    private lateinit var defaultTimeZone: TimeZone

    @Before
    fun setUp() {
        defaultTimeZone = TimeZone.getDefault()
        // Fuseau fixe pour un résultat de formatage déterministe (Phase 32).
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(defaultTimeZone)
    }

    @Test
    fun test_formattedTimeRange_formatsAsHHmm() {
        // 2026-01-01 20:00:00 UTC -> 2026-01-01 22:00:00 UTC
        val program = LiveEpgProgram(
            title = "Le programme",
            description = null,
            startTimestamp = 1767297600L,
            endTimestamp = 1767304800L
        )

        assertEquals("20:00 - 22:00", program.formattedTimeRange())
    }

    @Test
    fun test_formattedTimeRange_padsSingleDigitHoursAndMinutes() {
        // 2026-01-01 05:05:00 UTC -> 2026-01-01 05:35:00 UTC
        val program = LiveEpgProgram(
            title = "Matinale",
            description = null,
            startTimestamp = 1767243900L,
            endTimestamp = 1767245700L
        )

        assertEquals("05:05 - 05:35", program.formattedTimeRange())
    }
}
