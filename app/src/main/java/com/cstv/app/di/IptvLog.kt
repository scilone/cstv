package com.cstv.app.di

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object IptvLog {
    private val logHistory = mutableListOf<String>()
    private const val MAX_LOGS = 150
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private fun addHistory(tag: String, level: String, message: String) {
        synchronized(logHistory) {
            val timestamp = dateFormat.format(Date())
            logHistory.add("[$timestamp] [$level/$tag] $message")
            if (logHistory.size > MAX_LOGS) {
                logHistory.removeAt(0)
            }
        }
    }

    fun getHistory(): String {
        return synchronized(logHistory) {
            logHistory.joinToString("\n")
        }
    }

    fun clearHistory() {
        synchronized(logHistory) {
            logHistory.clear()
        }
    }

    fun d(tag: String, message: String) {
        addHistory(tag, "D", message)
        try {
            android.util.Log.d(tag, message)
        } catch (e: RuntimeException) {
            println("[$tag] $message")
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val errMsg = if (throwable != null) "$message: ${throwable.message}" else message
        addHistory(tag, "E", errMsg)
        try {
            android.util.Log.e(tag, message, throwable)
        } catch (e: RuntimeException) {
            println("[$tag] $message")
            throwable?.printStackTrace()
        }
    }

    fun w(tag: String, message: String) {
        addHistory(tag, "W", message)
        try {
            android.util.Log.w(tag, message)
        } catch (e: RuntimeException) {
            println("[$tag] $message")
        }
    }
}
