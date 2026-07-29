package com.cstv.app.data.util

import android.content.Context
import android.os.Build
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.di.IptvLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialsManager: CredentialsManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logLines = Collections.synchronizedList(mutableListOf<String>())
    private val maxLines = 1000

    val logFile: File by lazy {
        File(context.cacheDir, "app_debug_log.txt")
    }

    val crashFile: File by lazy {
        File(context.cacheDir, "crash_log.txt")
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun initialize() {
        setupCrashHandler()
    }

    fun startLogging() {
        loadLogsFromFile()

        IptvLog.logListener = { level, tag, msg, throwable ->
            val timestamp = dateFormat.format(Date())
            val stackTrace = throwable?.let { "\n" + getStackTraceString(it) } ?: ""
            val logLine = "[$level $timestamp] [$tag] $msg$stackTrace"
            
            appendLog(logLine)
        }
    }

    fun stopLogging() {
        IptvLog.logListener = null
        synchronized(logLines) {
            logLines.clear()
        }
        if (logFile.exists()) {
            logFile.delete()
        }
        if (crashFile.exists()) {
            crashFile.delete()
        }
    }

    private fun appendLog(line: String) {
        synchronized(logLines) {
            logLines.add(line)
            if (logLines.size > maxLines) {
                logLines.removeAt(0)
            }
        }
        scope.launch {
            try {
                logFile.appendText(line + "\n")
                // Limit file size to about 1.5MB
                if (logFile.length() > 1.5 * 1024 * 1024) {
                    val lines = logFile.readLines()
                    if (lines.size > 500) {
                        val subList = lines.subList(lines.size - 500, lines.size)
                        logFile.writeText(subList.joinToString("\n") + "\n")
                    }
                }
            } catch (e: Exception) {
                // Avoid infinite recursive logging loop
            }
        }
    }

    private fun loadLogsFromFile() {
        if (logFile.exists()) {
            try {
                val lines = logFile.readLines()
                synchronized(logLines) {
                    logLines.clear()
                    val start = if (lines.size > maxLines) lines.size - maxLines else 0
                    logLines.addAll(lines.subList(start, lines.size))
                }
            } catch (e: Exception) {
                // Avoid loop
            }
        }
    }

    fun saveCrash(throwable: Throwable) {
        try {
            val writer = StringWriter()
            throwable.printStackTrace(PrintWriter(writer))
            val crashReport = buildString {
                append("Crash Time: ").append(dateFormat.format(Date())).append("\n")
                append("Memory at Crash: ").append(getMemoryReport()).append("\n")
                append("Stacktrace:\n").append(writer.toString()).append("\n")
            }
            crashFile.writeText(crashReport)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (defaultHandler?.javaClass?.name?.contains("DiagnosticManager") == true) return

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val prefs = context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
            val debugEnabled = prefs.getBoolean("debug_mode_enabled", false)
            if (debugEnabled) {
                saveCrash(throwable)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun getMemoryReport(): String {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val usedMemory = totalMemory - freeMemory
        val percentage = if (maxMemory > 0) (usedMemory.toDouble() / maxMemory.toDouble() * 100).toInt() else 0
        return "Max Heap: ${maxMemory}MB, Allocated: ${totalMemory}MB, Free in Allocated: ${freeMemory}MB, Used: ${usedMemory}MB ($percentage%)"
    }

    fun generateReport(): String {
        return buildString {
            append("======================================================================\n")
            append("IPTV APPLICATION DIAGNOSTIC REPORT\n")
            append("======================================================================\n")
            append("Generated at: ").append(dateFormat.format(Date())).append("\n\n")

            append("--- SYSTEM INFO ---\n")
            append("App VersionName: ").append(getVersionName()).append("\n")
            append("Android Version: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Device Brand: ").append(Build.BRAND).append("\n")
            append("Device Model: ").append(Build.MODEL).append("\n")
            append("Available Processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n\n")

            append("--- JVM MEMORY STATE ---\n")
            append(getMemoryReport()).append("\n\n")

            append("--- LAST CRASH (PREVIOUS RUN) ---\n")
            if (crashFile.exists()) {
                try {
                    append(crashFile.readText()).append("\n")
                } catch (e: Exception) {
                    append("Error reading crash log: ").append(e.message).append("\n")
                }
            } else {
                append("No crash recorded on previous run.\n")
            }
            append("\n")

            append("--- ACTIVITY LOGS ---\n")
            val logs = synchronized(logLines) { logLines.toList() }
            if (logs.isEmpty()) {
                append("No logs recorded yet (or mode debug recently activated).\n")
            } else {
                logs.forEach { append(it).append("\n") }
            }
            append("\n======================================================================\n")
        }
    }

    private fun getVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun sanitize(text: String): String {
        var result = text
        val credentials = credentialsManager.getCredentials()
        if (credentials != null) {
            val username = credentials.username
            val password = credentials.password
            val host = credentials.host
            
            if (username.isNotEmpty()) {
                result = result.replace(username, "[REDACTED_USER]", ignoreCase = true)
            }
            if (password.isNotEmpty()) {
                result = result.replace(password, "[REDACTED_PASS]", ignoreCase = true)
            }
            if (host.isNotEmpty() && host != "localhost") {
                result = result.replace(host, "[REDACTED_HOST]", ignoreCase = true)
            }
        }
        // Scrub patterns like: /live/username/password/
        result = result.replace(Regex("/(live|movie|series)/([^/]+)/([^/]+)/"), "/$1/[REDACTED_USER]/[REDACTED_PASS]/")
        // query params
        result = result.replace(Regex("(?i)username=[^&\\s/]+"), "username=[REDACTED_USER]")
        result = result.replace(Regex("(?i)password=[^&\\s/]+"), "password=[REDACTED_PASS]")
        
        return result
    }

    suspend fun uploadLogs(): String {
        val report = generateReport()
        val sanitizedReport = sanitize(report)

        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val mediaType = "text/plain; charset=utf-8".toMediaType()
        val requestBody = sanitizedReport.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://paste.rs")
            .post(requestBody)
            .build()

        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RuntimeException("HTTP Error: ${response.code} ${response.message}")
                }
                val body = response.body?.string()?.trim()
                if (body.isNullOrEmpty() || !body.startsWith("http")) {
                    throw RuntimeException("Invalid response from paste.rs: $body")
                }
                body
            }
        }
    }

    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
