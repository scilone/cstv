package com.cstv.app.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class IptvLogTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Before
    fun setUp() {
        IptvLog.logListener = null
    }

    @Test
    fun test_logListener_notifiedOnDebug() {
        var notifiedLevel: String? = null
        var notifiedTag: String? = null
        var notifiedMsg: String? = null
        
        IptvLog.logListener = { level, tag, msg, _ ->
            notifiedLevel = level
            notifiedTag = tag
            notifiedMsg = msg
        }
        
        IptvLog.d("TestTag", "Hello Debug")
        
        assertEquals("D", notifiedLevel)
        assertEquals("TestTag", notifiedTag)
        assertEquals("Hello Debug", notifiedMsg)
    }

    @Test
    fun test_logListener_notifiedOnWarning() {
        var notifiedLevel: String? = null
        var notifiedTag: String? = null
        var notifiedMsg: String? = null
        
        IptvLog.logListener = { level, tag, msg, _ ->
            notifiedLevel = level
            notifiedTag = tag
            notifiedMsg = msg
        }
        
        IptvLog.w("WarnTag", "Hello Warning")
        
        assertEquals("W", notifiedLevel)
        assertEquals("WarnTag", notifiedTag)
        assertEquals("Hello Warning", notifiedMsg)
    }

    @Test
    fun test_logListener_notifiedOnError() {
        var notifiedLevel: String? = null
        var notifiedTag: String? = null
        var notifiedMsg: String? = null
        var notifiedException: Throwable? = null
        
        IptvLog.logListener = { level, tag, msg, throwable ->
            notifiedLevel = level
            notifiedTag = tag
            notifiedMsg = msg
            notifiedException = throwable
        }
        
        val ex = RuntimeException("Crash!")
        IptvLog.e("ErrorTag", "Hello Error", ex)
        
        assertEquals("E", notifiedLevel)
        assertEquals("ErrorTag", notifiedTag)
        assertEquals("Hello Error", notifiedMsg)
        assertNotNull(notifiedException)
        assertEquals("Crash!", notifiedException?.message)
    }
}
