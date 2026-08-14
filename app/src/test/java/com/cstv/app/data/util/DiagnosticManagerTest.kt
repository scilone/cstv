package com.cstv.app.data.util

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.di.IptvLog
import com.cstv.app.domain.model.Credentials
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.io.File

class DiagnosticManagerTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var credentialsManager: CredentialsManager

    @Mock
    private lateinit var packageManager: PackageManager

    @Mock
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var cacheDir: File
    private lateinit var diagnosticManager: DiagnosticManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        
        // Setup cache directory for testing
        cacheDir = File.createTempFile("temp_cache", "").parentFile!!.resolve("diag_test_cache")
        cacheDir.mkdirs()
        whenever(context.cacheDir).thenReturn(cacheDir)
        
        // Setup package info
        val packageInfo = PackageInfo().apply {
            versionName = "1.64.8"
        }
        whenever(context.packageName).thenReturn("com.cstv.app")
        whenever(context.packageManager).thenReturn(packageManager)
        whenever(packageManager.getPackageInfo(eq("com.cstv.app"), any<Int>())).thenReturn(packageInfo)
        
        diagnosticManager = DiagnosticManager(context, credentialsManager)
        IptvLog.logListener = null
    }

    @Test
    fun test_sanitize_redactsCredentialsAndHost() {
        val creds = Credentials(
            host = "my-secret-server.com",
            port = 8080,
            username = "john_doe",
            password = "supersecretpassword123"
        )
        whenever(credentialsManager.getCredentials()).thenReturn(creds)

        val logLine = "Connecting to http://my-secret-server.com:8080/player_api.php?username=john_doe&password=supersecretpassword123 in live mode /live/john_doe/supersecretpassword123/456.ts"
        val sanitized = diagnosticManager.sanitize(logLine)
        println("DEBUG SANITIZED: $sanitized")

        assertFalse(sanitized.contains("john_doe"))
        assertFalse(sanitized.contains("supersecretpassword123"))
        assertFalse(sanitized.contains("my-secret-server.com"))
        
        assertTrue(sanitized.contains("[REDACTED_USER]"))
        assertTrue(sanitized.contains("[REDACTED_PASS]"))
        assertTrue(sanitized.contains("[REDACTED_HOST]"))
        assertTrue(sanitized.contains("/live/[REDACTED_USER]/[REDACTED_PASS]/"))
    }

    @Test
    fun test_loggingLifecycle_capturesLogsCorrectly() {
        diagnosticManager.startLogging()
        assertNotNull(IptvLog.logListener)

        IptvLog.d("TestTag", "Testing diagnostic logging")
        
        val report = diagnosticManager.generateReport()
        assertTrue(report.contains("Testing diagnostic logging"))
        assertTrue(report.contains("TestTag"))
        
        diagnosticManager.stopLogging()
        assertNull(IptvLog.logListener)
    }

    @Test
    fun test_crashPersistence_recordsCrashReport() {
        val exception = RuntimeException("Oh no, OOM simulator")
        diagnosticManager.saveCrash(exception)
        
        assertTrue(diagnosticManager.crashFile.exists())
        val report = diagnosticManager.generateReport()
        
        assertTrue(report.contains("Oh no, OOM simulator"))
        assertTrue(report.contains("RuntimeException"))
        
        diagnosticManager.stopLogging()
        assertFalse(diagnosticManager.crashFile.exists())
    }
}
